package oarlib.exceptions;

/**
 * Exception that should be thrown when a GraphReader object is told to read using one format
 * and the file specified does not appear to be in that format.
 * 
 * @author oliverlum
 *
 */
public class FormatMismatchException extends Exception{
	public FormatMismatchException() { super(); }
	public FormatMismatchException(String message) { super(message); }
	public FormatMismatchException(String message, Throwable cause) {super(message,cause);}
	public FormatMismatchException(Throwable cause){super(cause);}
}