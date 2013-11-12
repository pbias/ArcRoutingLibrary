package oarlib.exceptions;

/**
 * Exception that should be thrown when demands aren't set and some problem which requires demands 
 * to be set is being solved.  
 * 
 * @author oliverlum
 *
 */
public class NoDemandSetException extends Exception{
	public NoDemandSetException() { super(); }
	public NoDemandSetException(String message) { super(message); }
	public NoDemandSetException(String message, Throwable cause) {super(message,cause);}
	public NoDemandSetException(Throwable cause){super(cause);}
}