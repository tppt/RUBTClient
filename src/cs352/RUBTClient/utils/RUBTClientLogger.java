package cs352.RUBTClient.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/*************************************************************************************
 * A singleton class for a generally configured Logger object for use with various
 * classes within RUBTClientProject.
 * 
 * A class can request a logger object by obtaining this classes instance and calling
 * getLogger( String class_name, String log_file_name ).  This method generates
 * a Logger object for the class with class_name, creates a path to the intended
 * log_file_name log file location if necessary, and returns the Logger.
 * 
 * The Logger object obtained via this class is configured to never write to the
 * terminal, have a log file size limit of 1MB, and have a rotation of five possible
 * log files to rotate through if more size is needed or too many log files have been
 * created.
 * 
 * @author Thomas Travis
 * @version 1.0
 * @since 8/4/2013
 * For CS352 2013 Summer Session
 *
 *************************************************************************************/
public class RUBTClientLogger {

	protected static RUBTClientLogger instance = null;
	
	/**
	 * Obtain a logger for the calling class.  This logger logs all output to a file in
	 * "..\logs\class_name\log_file_name.id-unique_id.gen-generation_id.xml"
	 * where unique_id and generation_id are dynamically generated.  The "..\logs\" folder is located
	 * in the project file path.  
	 * 
	 * This particular logger configuration has a log size limit of 1MB and a count of five; that is, there can only
	 * ever be five logs saved at any time.  If a single logging session exceeds the limit, the logger continues the session
	 * in the next log file in the rotation.  All logs are written to the log file(s); no messages are written to 
	 * the terminal.
	 * 
	 * @param class_name - the name of the class this Logger object is intended to be used in conjunction with.  It
	 * is recommended that class_name be obtained via this.getClass().getName() in the calling class to reduce 
	 * ambiguity.
	 * 
	 * @param log_file_name - the intended general file_name.  If this string is empty or null, this method sets
	 * log_file_name = class_name.
	 * 
	 * @return the configured Logger object for the calling class
	 * 
	 * @author Thomas Travis
	 */
	public Logger getLogger( String class_name, String log_file_name ){
		
		//Create a new logger and disallow legacy settings.
		Logger new_logger = Logger.getLogger(class_name);
		new_logger.setUseParentHandlers(false);
		
		//Explicitly turn off the console handler, so we don't print to console.
		ConsoleHandler ch = new ConsoleHandler();
		ch.setLevel(Level.OFF);
		new_logger.addHandler( ch );
		
		//Construct the path to the directory into which we will save this logger's log files.
		String log_directory = System.getProperty("user.dir")+File.separator+"logs"+File.separator+class_name+File.separator;
		
		//If the directory doesn't already exist in the file system, create it.
		File f = new File( log_directory );
		if( !f.exists() ){
			try{
				Path directory_path = Paths.get(log_directory);
				Files.createDirectories(directory_path);
			}
			catch( IOException e ){
				System.err.println("An I/O error occurred while trying to create the "+class_name+" log file directory.");
			}
		}
		
		//If we're not provided a log file name to use, use the classes nae by default.
		if( log_file_name == null ||log_file_name.equals("") )
			log_file_name = class_name;
		
		//Generate the string pattern the log file will use to denote id and generation.
		String pattern = log_directory+log_file_name+".id-%u.gen-%g.xml";
		
		//Instantiate limit and count values.
		int limit = 1048576;
		int count = 5;
		
		//Generate a new FileHandler with the above pattern, limit, and count, then added it to the logger.
		try {
			
			FileHandler fh = new FileHandler(pattern, limit, count);
			new_logger.addHandler( fh );
		
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		//Pass the configured logger back to the calling class.
		return new_logger;
	}
	
	/**
	 * Obtain the singleton instance of RUBTClientLogger.  If the instance does not yet exist, 
	 * this method creates it.
	 * 
	 * @return the singleton instance of this class
	 * 
	 * @author Thomas Travis
	 */
	public static RUBTClientLogger getInstance(){
		if( instance == null )
			instance = new RUBTClientLogger();
		return instance;
	}
	
	protected RUBTClientLogger(){}
}
