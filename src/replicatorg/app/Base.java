/*
 Base.java

 Main class for the app.

 Part of the ReplicatorG project - http://www.replicat.org
 Copyright (c) 2008 Zach Smith

 Forked from Arduino: http://www.arduino.cc
 
 Based on Processing http://www.processing.org
 Copyright (c) 2004-05 Ben Fry and Casey Reas
 Copyright (c) 2001-04 Massachusetts Institute of Technology

 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation; either version 2 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software Foundation,
 Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package replicatorg.app;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * Primary role of this class is for platform identification and general
 * interaction with the system (launching URLs, loading files and images, etc)
 * that comes from that.
 */
public class Base {
	/**
	 * The version number of this edition of replicatorG.
	 */
	public static final int VERSION = 40;
	
	/**
	 * The textual representation of this version (4 digits, zero padded).
	 */
	public static final String VERSION_NAME = String.format("%04d-Beta",VERSION);

	/**
	 * The user preferences store.
	 */
	static public Preferences preferences = getUserPreferences();

	/**
	 * The general-purpose logging object.
	 */
	public static Logger logger = Logger.getLogger("replicatorg.log");
	public static FileHandler logFileHandler = null;
	public static String logFilePath = null;
	
	
    {	
		String levelName = Base.preferences.get("replicatorg.debuglevel", Level.INFO.getName());
		Level l = Level.parse(levelName);
		logger.setLevel(l);
	}

	/**
	 * This is the name of the alternate preferences set that this instance of
	 * ReplicatorG uses. If null, this instance will use the default preferences
	 * set.
	 */
	static private String alternatePrefs = null;

	/**
	 * Get the preferences node for ReplicatorG.
	 */
	static Preferences getUserPreferences() {
		Preferences prefs = Preferences.userNodeForPackage(Base.class);
		if (alternatePrefs != null) {
			prefs = prefs.node("alternate/"+alternatePrefs);
		}
		return prefs;
	}
	
	/**
	 * Singleton NumberFormat used for parsing and displaying numbers to GUI in the 
	 * localized format. Use for all non-GCode, numbers output and input.
	 */
	static private NumberFormat localNF = NumberFormat.getInstance();
	static public NumberFormat getLocalFormat() {
		return localNF;
	}
	
	/** Singleton Gcode NumberFormat: Unsed for writing the correct precision strings
	 * when generating gcode (minimum one decimal places) using . as decimal separator
	 */
	static private NumberFormat gcodeNF;
	{
		// We don't use DFS.getInstance here to maintain compatibility with Java 5
        DecimalFormatSymbols dfs;
 	 	gcodeNF = new DecimalFormat("##0.0##");
 	 	dfs = ((DecimalFormat)gcodeNF).getDecimalFormatSymbols();
 	 	dfs.setDecimalSeparator('.');
	}
	static public NumberFormat getGcodeFormat() {
		return gcodeNF;
	}

	/** enum for fast/easy OS checking */
	public enum Platform {
		WINDOWS, MACOS9, MACOSX, LINUX, OTHER
	}
	/** enum for fast/easy arch checking */
	public enum Arch {
		x86_64, x86, ARM, PPC, OTHER
	}
	
	/**
	 * Full name of the Java version (i.e. 1.5.0_11). Prior to 0125, this was
	 * only the first three digits.
	 */
	public static final String javaVersionName = System.getProperty("java.version");

	/**
	 * Current platform in use
	 */
	static public Platform platform;

	static public Arch arch; 
	
	/**
	 * Current platform in use.
	 * <P>
	 * Equivalent to System.getProperty("os.name"), just used internally.
	 */
	static public String platformName = System.getProperty("os.name");

	static {
		// figure out which operating system
		// this has to be first, since editor needs to know

		if (platformName.toLowerCase().indexOf("mac") != -1) {
			// can only check this property if running on a mac
			// on a pc it throws a security exception and kills the applet
			// (but on the mac it does just fine)
			if (System.getProperty("mrj.version") != null) { // running on a
																// mac
				platform = (platformName.equals("Mac OS X")) ? Platform.MACOSX : Platform.MACOS9;
			}

		} else {
			String osname = System.getProperty("os.name");

			if (osname.indexOf("Windows") != -1) {
				platform = Platform.WINDOWS;

			} else if (osname.equals("Linux")) { // true for the ibm vm
				platform = Platform.LINUX;

			} else {
				platform = Platform.OTHER;
			}
			String aString = System.getProperty("os.arch");
			if("i386".equals(aString)) 
				arch = Arch.x86;
			else if("x86_64".equals(aString) || "amd64".equals(aString) )
				arch =  Arch.x86_64;
			else if("universal".equals(aString) || "ppc".equals(aString)) {
				arch = Arch.OTHER;
				throw new RuntimeException("Can not use use arch: '" + arch + "'");
			}
			

			
		}
	}
	
	/**
	 * returns true if the ReplicatorG is running on a Mac OS machine,
	 * specifically a Mac OS X machine because it doesn't run on OS 9 anymore.
	 */
	static public boolean isMacOS() {
		return platform == Platform.MACOSX;
	}

	/**
	 * returns true if running on windows.
	 */
	static public boolean isWindows() {
		return platform == Platform.WINDOWS;
	}
	
	/**
	 * true if running on linux.
	 */
	static public boolean isLinux() {
		return platform == Platform.LINUX;
	}

	/** 
	 * Retrieves the application data directory via OS specific voodoo.
	 * Defaults to the current directory if no os specific settings exist, 
	 * @return File object pointing to the OS specific ApplicationsDirectory
	 */
	static public File getApplicationDirectory() {
		if( isMacOS() ) { 
			try { 
				File x = new File(".");
				String baseDir = x.getCanonicalPath();
				//baseDir = baseDir + "/ReplicatorG.app/Contents/Resources";
				//Base.logger.severe("OSX AppDir at " + baseDir );
				//we want to use ReplicatorG.app/Content as our app dir.
				if(new File(baseDir + "/ReplicatorG.app/Contents/Resources").exists())
					return new File(baseDir + "/ReplicatorG.app/Contents/Resources");
				else
					Base.logger.severe(baseDir + "/ReplicatorG.app not found, using " + baseDir + "/replicatorg/");
					return new File(baseDir + "/replicatorg");
				}
			catch (java.io.IOException e) {
				// This space intentionally left blank. Fall through.
			}
		}
		return new File(System.getProperty("user.dir"));
	}
	
	static public File getApplicationFile(String path) {
		return new File(getApplicationDirectory(), path);
	}

	/**
	 * Get the the user preferences and profiles directory. By default this is
	 * ~/.replicatorg; if an alternate preferences set is selected, it will
	 * instead be ~/.replicatorg/alternatePrefs/<i>alternate_prefs_name</i>.
	 */
	static public File getUserDirectory() {
		String path = System.getProperty("user.home")+File.separator+".replicatorg";
		if (alternatePrefs != null) { path = path + File.separator + alternatePrefs; }
		File dir = new File(path);
		if (!dir.exists()) {
			dir.mkdirs();
			if( ! dir.exists() )  { // we failed to create our user dir. Log the failure, try to continue
				Base.logger.severe("We could not create a user directory at: "+ path );
				return null; 
			}
		}

		return dir;
	}
	
	/**
	 * 
	 * @param path The relative path to the file in the .replicatorG directory
	 * @param autoCopy If true, copy over the file of the same name in the application directory if none is found in the prefs directory.
	 * @return
	 */
	static public File getUserFile(String path, boolean autoCopy) {
		if (path.contains("..")) {
			Base.logger.info("Attempted to access parent directory in "+path+", skipping");
			return null;
		}
		// First look in the user's local .replicatorG directory for the path.
		File f = new File(getUserDirectory(),path);
		// Make the parent file if not already there
		File dir = f.getParentFile();
		if (!dir.exists()) { dir.mkdirs(); }
		if (autoCopy && !f.exists()) {
			// Check if there's an application-level version
			File original = getApplicationFile(path);
			// If so, copy it over
			if (original.exists()) {
				try {
					Base.copyFile(original,f);
				} catch (IOException ioe) {
					Base.logger.log(Level.SEVERE,"Couldn't copy "+path+" to your local .replicatorG directory",f);
				}
			}
		}
		return f;
	}
	
	static public void copyFile(File afile, File bfile) throws IOException {
		InputStream from = new BufferedInputStream(new FileInputStream(afile));
		OutputStream to = new BufferedOutputStream(new FileOutputStream(bfile));
		byte[] buffer = new byte[16 * 1024];
		int bytesRead;
		while ((bytesRead = from.read(buffer)) != -1) {
			to.write(buffer, 0, bytesRead);
		}
		to.flush();
		from.close(); // ??
		from = null;
		to.close(); // ??
		to = null;

		bfile.setLastModified(afile.lastModified()); // jdk13+ required
		// } catch (IOException e) {
		// e.printStackTrace();
		// }
	}
	
	static public void copyDir(File sourceDir, File targetDir)
			throws IOException {
		targetDir.mkdirs();
		String files[] = sourceDir.list();
		for (int i = 0; i < files.length; i++) {
			if (files[i].equals(".") || files[i].equals(".."))
				continue;
			File source = new File(sourceDir, files[i]);
			File target = new File(targetDir, files[i]);
			if (source.isDirectory()) {
				// target.mkdirs();
				copyDir(source, target);
				target.setLastModified(source.lastModified());
			} else {
				copyFile(source, target);
			}
		}
	}	
}
