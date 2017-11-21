
/**
 * FastFtp Class
 * 
 * @author Shannon Tucker-Jones 10101385 (template code by Majid Ghaderi)
 * @version Nov 16, 2017
 *
 */

import java.io.*;
import java.net.*;
import java.util.Timer;
import cpsc441.a3.shared.*;

public class FastFtp extends Thread
{
	//Initialize window size variable
	public volatile int window;
	//Initialize timeout variable
	public volatile int timeout;
	//Initialize transmission queue
	public volatile TxQueue queue;
	//Initialize sockets
	public volatile Socket tcp = null;
	public volatile DatagramSocket udp = null;
	//Initialize data streams
	public volatile DataInputStream dataIn = null;
	public volatile DataOutputStream dataOut = null;	
	//Other important variables
	public volatile Thread t1 = null;
	public volatile String sName = null;
	public volatile int receivePort;
	public volatile File f;
	
	/**
     * Constructor to initialize the program 
     * 
     * @param windowSize	Size of the Go-Back_N window
     * @param rtoTimer		The timeout interval for the retransmission timer
     */
	public FastFtp(int windowSize, int rtoTimer) 
	{
		//Set window size
		window = windowSize;
		queue = new TxQueue(window);
		
		//Set timeout interval (in milliseconds)
		timeout = rtoTimer;
	}
	

    /**
     * Sends the specified file to the specified destination host:
     * 1. Sends file/connection info over TCP
     * 2. Starts receiving thread to process incoming ACKs
     * 3. Starts sender thread to send packets
     * 
     * @param serverName	Name of the remote server
     * @param serverPort	Port number of the remote server
     * @param fileName		Name of the file to be transferred to the remote server
     */
	public void send(String serverName, int serverPort, String fileName) 
	{
				
		try
		{
			//Open TCP connection to server
			//Create TCP and UDP client sockets
			tcp = new Socket(serverName, serverPort);
			udp = new DatagramSocket();
			
			//Create data streams
			dataIn = new DataInputStream(tcp.getInputStream());
			dataOut = new DataOutputStream(tcp.getOutputStream());
			
			//Create file object
			f = new File(fileName);	
			
			sName = serverName;
			
			System.out.println("\nTCP HANDSHAKE");
			
			try
			{
				//Send file name over TCP
				dataOut.writeUTF(fileName);		
				System.out.println("Sent file name");
				
				//Send file length (in bytes) over TCP
				dataOut.writeLong(f.length());		
				System.out.println("Sent file length");	
				
				//Send local UDP port number over TCP
				dataOut.writeInt(udp.getLocalPort());		
				System.out.println("Sent local UDP port number:" + udp.getLocalPort());
				
				dataOut.flush();
				
				//Receive server UDP port number over TCP
				receivePort = dataIn.readInt();	
				System.out.println("Received server's UDP port number: " + receivePort);
				
							
			}
			catch (IOException e)
			{
				//Check where error is
				e.printStackTrace();
				e.getMessage();				
			}
			
			System.out.println("Handshake complete.");	
			
			//Start ACK receiving thread
			t1 = new Thread(new Ack(this));
			t1.start();
			
			//Start sender thread
			Thread t2 = new Thread(new Send(this));
			t2.start();		
			
		}
		catch (Exception e)
		{
			//Check where error is
			e.printStackTrace();
			e.getMessage();
		}
		
	}
	
	/**
     * Processes ACKs that are received from the server:
     * 1. Continuously listens for ACKS
     * 2. Receives ACK, gets ACK number
     * 3. Checks if received ACK corresponds to a Segment in the current window
     * 4. If Segment is found in the window, removes queue elements with sequence numbers less than the ACK number
     * 5. If queue is not empty, restarts the timer
     * 6. If queue is empty, breaks out of the loop 
     */	
	public synchronized void processACK()
	{
		while(true)
		{
			System.out.println("\nACK RECEIVER");
			
			//Create byte array and DatagramPacket for received data
			byte[] receiveData = new byte[Segment.MAX_PAYLOAD_SIZE];
			DatagramPacket receivePkt = null;
			
			try
			{
				System.out.println("Listening for ACKs...");
				
				//Create packet object to receive
				receivePkt = new DatagramPacket(receiveData, receiveData.length);		
		
				//Receive ACK
				udp.receive(receivePkt);
			}
			catch (SocketTimeoutException ex)
			{
				//Check where error is
				processTimeout();			
			}				
			catch (IOException e)
			{
				//Check where error is
				e.printStackTrace();
				e.getMessage();				
			}
			
			receiveData = receivePkt.getData();
			
			//Create segment from the received DatagramPacket
			Segment ackSeg = new Segment(receiveData);
			
			//Get ACK sequence number	
			int ackNum = ackSeg.getSeqNum();
			int seq = ackNum - 1;
			
			System.out.println("\nACK RECEIVER\nReceived ACK number: " + ackNum);
			
			//Get segments in current window
			Segment[] checkWindow = queue.toArray();
			
			boolean inWindow = false;
			
			//Iterate over segments in current window
			for(int i = 0; i < checkWindow.length; i++)
			{	
				//Check if ACK is in current window			
				if(checkWindow[i].getSeqNum() == seq)
					inWindow = true;
			}
			
			System.out.println("\nACK RECEIVER\nElement with sequence number " + seq + " in window: " + inWindow);
			
			//If ACK is in current window...
			if(inWindow)
			{
				//Cancel timer
				try
				{
					udp.setSoTimeout(0);
				}
				catch (Exception e)
				{
					//Check where error is
					e.printStackTrace();
					e.getMessage();				
				}
				
				//Remove queue elements that have been successfully transmitted
				while(!queue.isEmpty() && queue.element().getSeqNum() < ackNum)
				{
					
					System.out.println("\nACK RECEIVER\nRemoving queue element with sequence number: " + queue.element().getSeqNum());

					try
					{
						//Remove head of queue
						queue.remove();
					}
					catch (Exception e)
					{
						//Check where error is
						e.printStackTrace();
						e.getMessage();						
					}
				}
				
				//If queue is not empty, restart the timer
				if(!queue.isEmpty())
				{			
					try
					{
						System.out.println("\nACK RECEIVER\nRestarting timer in ACK RECEIVER...");
						
						//Set timer to timeout value
						udp.setSoTimeout(timeout);
					}
					catch (Exception e)
					{
						//Check where error is
						e.printStackTrace();
						e.getMessage();				
					}
				}
			}
			
			try
			{
				//Yield ACK execution so Send can run
				Thread.yield();
			}
			catch(Exception e)
			{
				//Check where error is
				e.printStackTrace();
				e.getMessage();	
			}
			
			//If queue is empty, break out of the loop
			if(queue.isEmpty())
			{
				System.out.println("\nACK RECEIVER DONE, BREAKING OUT OF LOOP\n");
				break;
			}
		}
	}	

	/**
     * Processes timeouts:
     * 1. Gets pending Segments from the queue
     * 2. Resends all of the pending Segments
     * 3. If queue is not empty, restarts the timer
     */
	public synchronized void processTimeout()
	{		
		//Get list of pending segments from the transmission queue
		Segment[] pending = queue.toArray();
		
		//Go through list and send all segments to UDP socket
		for(int i = 0; i < pending.length; i++)
		{	
			System.out.println("\nTIMEOUT\nResending segment number: " + pending[i].getSeqNum());
			
			try
			{
				//Create datagram object with data, data length, server IP, and server port number
				DatagramPacket sendPkt = new DatagramPacket(pending[i].getBytes(), pending[i].getBytes().length, InetAddress.getByName(sName), receivePort);
			
				//Send datagram to UDP socket
				udp.send(sendPkt);	
			}
			catch (Exception e)
			{
				//Check where error is
				e.printStackTrace();
				e.getMessage();				
			}		
		}		
		
		//If queue is not empty, restart the timer
		if(!queue.isEmpty())
		{
			try
			{
				System.out.println("\nTIMEOUT\nRestarting timer in TIMEOUT...");
							
				//Set timer to timeout value
				udp.setSoTimeout(timeout);
			}
			catch (Exception e)
			{
				//Check where error is
				e.printStackTrace();
				e.getMessage();				
			}
		}		
	}	
	
    /**
     * A simple test driver
     * 
     */
	public static void main(String[] args) 
	{
		// all arguments should be provided
		// as described in the assignment description 
		if (args.length != 5) 
		{
			System.out.println("ERROR: Wrong Number of Arguments.");
			System.out.println("USAGE: FastFtp <server port file window timeout>");
			System.exit(1);
		}
		
		//Parse command line arguments
		//Assume no errors
		
		//1st Argument = server name
		String serverName = args[0];
		//2nd Argument = server port number
		int serverPort = Integer.parseInt(args[1]);
		//3rd Argument = file name
		String fileName = args[2];
		//4th Argument = window size
		int windowSize = Integer.parseInt(args[3]);
		//5th Argument = timeout interval
		int timeout = Integer.parseInt(args[4]);

		//Set window size and timeout interval in constructor 
		FastFtp ftp = new FastFtp(windowSize, timeout);
		
		System.out.printf("sending file \'%s\' to server...\n", fileName);
		
		//Send file to server
		ftp.send(serverName, serverPort, fileName);
	}
}
