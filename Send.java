
/**
 * Send Class
 * 
 * @author Shannon Tucker-Jones 10101385
 * @version Nov 16, 2017
 * 
 */

import java.io.*;
import java.net.*;
import java.util.Timer;
import java.util.Arrays;
import cpsc441.a3.shared.*;

public class Send extends Thread 
{	
	//Initialize FastFtp (from main program)
	private volatile FastFtp ftp;	
	
	 /**
     * Constructor to initialize the Send thread
     * 
     * @param target	Instance of FastFtp that contains important variables (sockets, TxQueue, etc.)
     */
	public Send(FastFtp target)
	{
		//Get instance of FastFtp		
		ftp = target;
	}
	
	
	/**
	 * Sends packets to the server:
	 * 1. Reads a file chunk by chunk
	 * 2. Encapsulates each chunk into a Segment
	 * 3. Waits while the queue is full
	 * 4. Sends each Segment to the UDP server port
	 * 5. Starts the timer for the first Segment in the queue
	 * 6. When EOF is reached, waits until the queue is empty
	 * 7. Closes the ACK thread, data streams, and sockets
	 */		
	public void run()
	{
			FileInputStream fis = null;	
		
			System.out.println("\nSENDER");
				
			try
			{
				System.out.println("Reading file...");
				
				//Read file chunk by chunk
				//Max chunk size = MAX_PAYLOAD_SIZE
				fis = new FileInputStream(ftp.f);
				byte[] chunk = new byte[Segment.MAX_PAYLOAD_SIZE];
					
				int i = 0;
				int seqNum = 0;
				
				//While not end of file
				while((i = fis.read(chunk)) != -1)
				{
					//If chunk size > number of bytes that are read...
					if(chunk.length > i)
					{
						//Change size of the last chunk
						chunk = Arrays.copyOfRange(chunk, 0, i);
					}
					
					System.out.println("\nSENDER\nEncapsulating chunk with sequence number: " + seqNum);
					
					//Encapsulate each chunk in one segment
					Segment toSend = new Segment(seqNum, chunk);
						
					//Create datagram object with data, data length, server IP, and server port number
					DatagramPacket sendPkt = new DatagramPacket(toSend.getBytes(), toSend.getBytes().length, InetAddress.getByName(ftp.sName), ftp.receivePort);

					//Check if transmission queue full
					while(ftp.queue.isFull())
					{	
						//Wait while the queue is full
						try
						{
							//Yield Send execution so ACK can run
							Thread.yield();
						}
						catch(Exception e)
						{
							//Check where error is
							e.printStackTrace();
							e.getMessage();	
						}
					}
							
					try
					{
						System.out.println("\nSENDER\nSending packet " + toSend.getSeqNum() + " to UDP socket...");
						
						//Send datagram to UDP socket
						ftp.udp.send(sendPkt);
									
						//Add segment to transmission queue		
						ftp.queue.add(toSend);	
					}
					catch (Exception e)
					{
						//Check where error is
						e.printStackTrace();
						e.getMessage();
					}							
								
					//If this is the first segment in transmission queue, start the timer
					if(toSend == ftp.queue.element())
					{
						System.out.println("\nSENDER\nStarting timer for the first segment...");			
						
						//Set timer to timeout value
						ftp.udp.setSoTimeout(ftp.timeout);
					
					}
						
					//Increment sequence number		
					seqNum++;							
				}
				
				//Check if transmission queue empty
				while(!ftp.queue.isEmpty())
				{
					//Wait until the queue is empty
					try
					{
						//Yield Send execution so ACK can run					
						Thread.yield();
					}
					catch(Exception e)
					{
						//Check where error is
						e.printStackTrace();
						e.getMessage();	
					}
				}	
				
				System.out.println("\nCLOSING PROGRAM");
				System.out.println("Cancelling timer");
				
				//Cancel timer
				try
				{
					ftp.udp.setSoTimeout(0);
				}
				catch (Exception e)
				{
					//Check where error is
					e.printStackTrace();
					e.getMessage();				
				}
				
				System.out.println("Closing ACK receiver");				
				
				//Close ACK thread
				ftp.t1.interrupt();

				System.out.println("Closing data streams");
				
				//Close streams
				fis.close();				
				ftp.dataIn.close();
				ftp.dataOut.close();	
				
				System.out.println("Closing sockets");								
				
				//Close TCP and UDP sockets
				ftp.udp.close();
				ftp.tcp.close();									
			}
			catch (Exception e)
			{
				//Check where error is
				e.printStackTrace();
				e.getMessage();
			}
				
	}
}
