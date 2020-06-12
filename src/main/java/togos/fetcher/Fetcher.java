package togos.fetcher;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Base32 functions were originally written by Robert Kaye and Gordon Mohr
 * See: http://www.herongyang.com/encoding/Base32-Bitpedia-Java-Implementation.html
 */

/**
 * Requrements:
 * - Single class file (to minimize JAR size)
 * - Support fetching bu sha1: or bitprint: URNs
 * - Only need to support fetching a single file at a time
 * - Ability to try fetching from multiple repositories
 * - Check hash of file (just SHA-1 is fine) before completing
 * - Should be silent on success
 * - On error, should report repositories tried, hash mismatches, etc
 * 
 * Would be nice:
 * - Ablity to copy/link from local filesystem repositories, too
 */
public class Fetcher
{
	public static final String APPNAME = "TJFetcher";
	public static final String VERSION = "1.2.0";
	
	protected static final String UNPOSSIBLE = "This is unpossible!";
	
	// Base32
	
    private static final String base32Chars =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int[] base32Lookup =
    { 0xFF,0xFF,0x1A,0x1B,0x1C,0x1D,0x1E,0x1F, // '0', '1', '2', '3', '4', '5', '6', '7'
      0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF, // '8', '9', ':', ';', '<', '=', '>', '?'
      0xFF,0x00,0x01,0x02,0x03,0x04,0x05,0x06, // '@', 'A', 'B', 'C', 'D', 'E', 'F', 'G'
      0x07,0x08,0x09,0x0A,0x0B,0x0C,0x0D,0x0E, // 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O'
      0x0F,0x10,0x11,0x12,0x13,0x14,0x15,0x16, // 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W'
      0x17,0x18,0x19,0xFF,0xFF,0xFF,0xFF,0xFF, // 'X', 'Y', 'Z', '[', '\', ']', '^', '_'
      0xFF,0x00,0x01,0x02,0x03,0x04,0x05,0x06, // '`', 'a', 'b', 'c', 'd', 'e', 'f', 'g'
      0x07,0x08,0x09,0x0A,0x0B,0x0C,0x0D,0x0E, // 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o'
      0x0F,0x10,0x11,0x12,0x13,0x14,0x15,0x16, // 'p', 'q', 'r', 's', 't', 'u', 'v', 'w'
      0x17,0x18,0x19,0xFF,0xFF,0xFF,0xFF,0xFF  // 'x', 'y', 'z', '{', '|', '}', '~', 'DEL'
    };

    /**
     * Encodes byte array to Base32 String.
     *
     * @param bytes Bytes to encode.
     * @return Encoded byte array <code>bytes</code> as a String.
     *
     */
    static public String base32Encode(final byte[] bytes) {
        int i = 0, index = 0, digit = 0;
        int currByte, nextByte;
        StringBuffer base32 = new StringBuffer((bytes.length + 7) * 8 / 5);

        while (i < bytes.length) {
            currByte = (bytes[i] >= 0) ? bytes[i] : (bytes[i] + 256); // unsign

            /* Is the current digit going to span a byte boundary? */
            if (index > 3) {
                if ((i + 1) < bytes.length) {
                    nextByte =
                        (bytes[i + 1] >= 0) ? bytes[i + 1] : (bytes[i + 1] + 256);
                } else {
                    nextByte = 0;
                }

                digit = currByte & (0xFF >> index);
                index = (index + 5) % 8;
                digit <<= index;
                digit |= nextByte >> (8 - index);
                i++;
            } else {
                digit = (currByte >> (8 - (index + 5))) & 0x1F;
                index = (index + 5) % 8;
                if (index == 0)
                    i++;
            }
            base32.append(base32Chars.charAt(digit));
        }

        return base32.toString();
    }

    /**
     * Decodes the given Base32 String to a raw byte array.
     *
     * @param base32
     * @return Decoded <code>base32</code> String as a raw byte array.
     */
    static public byte[] base32Decode(final String base32) {
        int i, index, lookup, offset, digit;
        byte[] bytes = new byte[base32.length() * 5 / 8];

        for (i = 0, index = 0, offset = 0; i < base32.length(); i++) {
            lookup = base32.charAt(i) - '0';

            /* Skip chars outside the lookup table */
            if (lookup < 0 || lookup >= base32Lookup.length) {
                throw new RuntimeException("Invalid base32 character: '"+base32.charAt(i)+"'");
            }

            digit = base32Lookup[lookup];

            /* If this digit is not in the table, ignore it */
            if (digit == 0xFF) {
                continue;
            }

            if (index <= 3) {
                index = (index + 5) % 8;
                if (index == 0) {
                    bytes[offset] |= digit;
                    offset++;
                    if (offset >= bytes.length)
                        break;
                } else {
                    bytes[offset] |= digit << (8 - index);
                }
            } else {
                index = (index + 5) % 8;
                bytes[offset] |= (digit >>> index);
                offset++;

                if (offset >= bytes.length) {
                    break;
                }
                bytes[offset] |= digit << (8 - index);
            }
        }
        return bytes;
    }
	
	// Repo shorthand parsing, from ContentCouch3 Downloader
	
	protected static final Pattern BARE_HOSTNAME_REPO_PATTERN = Pattern.compile("^[^/]+$");
	protected static final Pattern BARE_HTTP_HOSTNAME_REPO_PATTERN = Pattern.compile("^https?://[^/]+$");
	protected static String defuzzRemoteRepoPrefix( String url ) {
		if( BARE_HOSTNAME_REPO_PATTERN.matcher(url).matches() ) {
			url = "http://" + url;
		}
		if( BARE_HTTP_HOSTNAME_REPO_PATTERN.matcher(url).matches() ) {
			url += "/uri-res/N2R?";
		}
		if( !url.endsWith("/") && !url.endsWith("?") ) {
			url += "?";
		}
		return url;
	}
	
	// URN parsing
	
	protected static final Pattern SHA1EXTRACTOR = Pattern.compile("^urn:(?:sha1|bitprint):([A-Z2-7]{32})");
	
	protected static final byte[] extractSha1( String urn ) {
		Matcher m = SHA1EXTRACTOR.matcher(urn);
		if( !m.find() ) {
			throw new RuntimeException("Invalid SHA-1 URN: "+urn);
		}
		return base32Decode(m.group(1));
	}
	
	// TODO: Do without an instance altogether
	
	public boolean debug = false;
	public boolean allowClobber = false;
	
	/**
	 * Error messages to be shown only if fetching fails for all repositories
	 */
	protected final ArrayList downloadErrors = new ArrayList();
	protected final ArrayList repoPrefixes;
	protected final String sourceUrn, destPath;
	
	public Fetcher( String urn, String outpath, ArrayList repoPrefixes ) {
		this.repoPrefixes = repoPrefixes;
		this.sourceUrn = urn;
		this.destPath = outpath;
	}
	
	protected byte[] download( InputStream is, OutputStream os, MessageDigest digestor )
		throws IOException
	{
		byte[] buffer = new byte[65536];
		for( int r; (r = is.read(buffer)) > 0 ; ) {
			digestor.update(buffer, 0, r);
			os.write(buffer, 0, r);
		}
		return digestor.digest();
	}
	
	protected static boolean equal( byte[] a, byte[] b ) {
		if( a.length != b.length ) return false;
		for( int i=a.length-1; i>=0; --i ) if( a[i] != b[i] ) return false;
		return true;
	}
	
	protected static final MessageDigest newSha1Digestor() {
		try {
			return MessageDigest.getInstance("SHA-1");
		} catch( NoSuchAlgorithmException e ) {
			throw new RuntimeException(UNPOSSIBLE, e);
		}
	}
	
	protected void debug( String text ) {
		if( debug ) System.err.println(text);
	}
	
	protected void close( Closeable c ) {
		if( c == null ) return;
		try { c.close(); } catch( IOException e ) { /*Whatever*/ }
	}
	
	protected boolean downloadFrom( String repoUrl, String urn, byte[] expectedHash, File outFile )
		throws MalformedURLException
	{
		Exception connectionError = null;
		URL fullUrl = new URL(repoUrl+urn);
		
		if( debug ) {
			System.err.print("Attempting download from "+fullUrl+"...");
			System.err.flush();
		}
		
		File outDir = outFile.getParentFile();
		if( outDir != null && !outDir.exists() ) outDir.mkdirs();
		
		InputStream is = null;
		FileOutputStream os = null;
		try {
			URLConnection urlC = fullUrl.openConnection();
			urlC.connect();
			is = urlC.getInputStream();
			try {
				os = new FileOutputStream(outFile);
			} catch( IOException e ) {
				downloadErrors.add("Failed to open "+outFile+" for writing.");
				return false;
			}
			byte[] hash = download( is, os, newSha1Digestor() );
			if( !equal(hash, expectedHash) ) {
				debug("Hash mismatch");
				downloadErrors.add("Bad data from "+fullUrl+"; sha1:"+base32Encode(hash));
				return false;
			}
			debug("Found!");
			return true;
		} catch( FileNotFoundException e ) {
			debug("404d!");
			// 404d!
		} catch( NoRouteToHostException e ) {
			connectionError = e;
		} catch( UnknownHostException e ) {
			connectionError = e;
		} catch( ConnectException e ) {
			connectionError = e;
		} catch( SocketException e ) {
			connectionError = e;
		} catch( IOException e ) {
			connectionError = e;
		} finally {
			close(is);
			close(os);
		}
		
		if( connectionError != null ) {
			debug("Connection error: "+connectionError.getMessage());
			downloadErrors.add(connectionError.getClass().getName()+" when downloading "+fullUrl+": "+connectionError.getMessage());
		}
		
		return false;
	}
	
	public int run() {
		File destFile = new File(destPath);
		if( !allowClobber && destFile.exists() ) return EXIT_OKAY;
		File destDir = destFile.getParentFile();
		Random r = new Random();
		String tempFileName = "."+sourceUrn.hashCode()+"-"+System.currentTimeMillis()+"-"+r.nextInt(Integer.MAX_VALUE)+".temp";
		File tempFile = new File(destDir, tempFileName);
		byte[] expectedHash = extractSha1(sourceUrn);
		for( int i=0; i<repoPrefixes.size(); ++i  ) {
			String rp = (String)repoPrefixes.get(i);
			try {
				if( downloadFrom(rp, sourceUrn, expectedHash, tempFile) ) {
					if( !tempFile.renameTo(destFile) ) {
						System.err.println("Failed to move temp file '"+tempFile+"' to destination '"+destFile+"'");
						return EXIT_EXCEPTION;
					}
					return 0;
				}
			} catch( MalformedURLException e ) {
				System.err.println(e.getMessage());
				return EXIT_USER_ERROR;
			}
		}
		for( int i=0; i<downloadErrors.size(); ++i ) {
			System.err.println("Error: "+downloadErrors.get(i));
		}
		System.err.println("Did not find "+sourceUrn+" in any repository");
		return EXIT_NOT_FOUND;
	}
	
	protected static final String USAGE =
		"Usage: tjfetcher [-debug] [-nc] -repo <url> ... -o <outfile> <urn>\n";
	protected static final int EXIT_OKAY       = 0;
	protected static final int EXIT_USER_ERROR = 1;
	protected static final int EXIT_NOT_FOUND  = 2;
	protected static final int EXIT_EXCEPTION  = 3;
	
	protected static String readSingleLine(File f) throws IOException {
		BufferedReader r = new BufferedReader(new FileReader(f));
		String line;
		while( (line = r.readLine()) != null ) {
			line = line.trim();
			if( line.isEmpty() || line.startsWith("#") ) continue;
			return line;
		}
		throw new IOException("No non-comment/non-empty lines found in "+f);
	}

	public static void main( String[] args ) {
		String urn = null;
		String outpath = null;
		ArrayList repoPrefixes = new ArrayList();
		boolean allowClobber = true;
		boolean debug = false;
		boolean anyUsageErrors = false;
		for( int i=0; i<args.length; ++i ) {
			if( "-repo".equals(args[i]) && i+1 < args.length ) {
				String repoArg = args[++i];
				if( repoArg.startsWith("@") ) {
					File repoListFile = new File(repoArg.substring(1));
					try {
						BufferedReader r = new BufferedReader(new FileReader(repoListFile));
						try {
							String line;
							while( (line = r.readLine()) != null ) {
								line = line.trim();
								if( line.isEmpty() || line.startsWith("#") ) continue;
								repoPrefixes.add(defuzzRemoteRepoPrefix(line));
							}
						} finally {
							r.close();
						}
					} catch( IOException e ) {
						System.err.println("Error reading repository list file: "+repoListFile+": "+e.getMessage());
					} 
				} else {
					repoPrefixes.add(defuzzRemoteRepoPrefix(repoArg));
				}
			} else if( "-debug".equals(args[i]) ) {
				debug = true;
			} else if( "-nc".equals(args[i]) ) {
				allowClobber = false;
			} else if( "-o".equals(args[i]) && i+1 < args.length ) {
				if( outpath == null ) {
					outpath = args[++i];
				} else {
					System.err.println("Error: Re-specification of output file from '"+outpath+"' to '"+args[++i]+"'");
					anyUsageErrors = true;
				}
			} else if( "-h".equals(args[i]) || "-?".equals(args[i]) || "--help".equals(args[i]) ) {
				System.out.println(APPNAME+" "+VERSION);
				System.out.print(USAGE);
				return;
			} else if( !args[i].startsWith("-") ) {
				if( urn == null ) {
					if( args[i].startsWith("@") ) {
						try {
							urn = readSingleLine(new File(args[i].substring(1)));
						} catch( IOException e ) {
							System.err.println("Error: "+e);
							System.exit(EXIT_USER_ERROR);
						}
					} else {
						urn = args[i];
					}
				} else if( outpath == null ) {
					outpath = args[i];
				} else {
					System.err.println("Error: Extraneous non-option argument: "+args[i]);
					anyUsageErrors = true;
				}
			} else {
				System.err.println("Error: Unrecognized argument: "+args[i]);
				anyUsageErrors = true;
			}
		}
		if( urn == null ) {
			System.err.println("Error: No URN specified");
			anyUsageErrors = true;
		}
		if( outpath == null ) {
			System.err.println("Error: No output file specified");
			anyUsageErrors = true;
		}
		if( repoPrefixes.size() == 0 ) {
			System.err.println("Error: No repositories specified");
			anyUsageErrors = true;
		}
		if( anyUsageErrors ) {
			System.err.println();
			System.err.println(USAGE);
			System.exit(EXIT_USER_ERROR);
		}
		
		Fetcher f = new Fetcher( urn, outpath, repoPrefixes );
		f.debug = debug;
		f.allowClobber = allowClobber;
		System.exit(f.run());
	}
}
