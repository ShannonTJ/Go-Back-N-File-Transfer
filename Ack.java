
/**
 * Ack Class
 * 
 * @author Shannon Tucker-Jones 10101385
 * @version Nov 16, 2017
 * 
 */

import java.io.*;
import java.net.*;
import cpsc441.a3.shared.*;

public class Ack extends Thread
{	
	//Initialize FastFtp (from main program)
	private volatile FastFtp ftp;	
	
	 /**
     * Constructor to initialize the Ack thread
     * 
     * @param target	Instance of FastFtp that contains processACK() and important variables (sockets, TxQueue, etc.)
     */
	public Ack(FastFtp target)
	{
		//Get instance of FastFtp
		ftp = target;
	}

	/**
	 * Runs processACK()
	 */	
	public void run()
	{
		//Run processACK() in FastFtp
		ftp.processACK();
	}
}
