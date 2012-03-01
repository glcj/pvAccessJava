/**
 * 
 */
package org.epics.ca.impl.remote.io.impl.test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.logging.Logger;

import org.epics.ca.CAConstants;
import org.epics.ca.impl.remote.TransportSendControl;
import org.epics.ca.impl.remote.TransportSender;
import org.epics.ca.impl.remote.codec.AbstractCodec;
import org.epics.ca.impl.remote.io.PollEvents;
import org.epics.ca.impl.remote.io.Poller;
import org.epics.ca.impl.remote.io.impl.PollerImpl;
import org.epics.pvData.pv.Field;

/**
 * @author msekoranja
 *
 */
public class UDPLatencyTest extends AbstractCodec implements PollEvents {

	interface ReadyListener {
		void ready(AbstractCodec codec);
	}
	
	final Poller poller;
	final DatagramChannel channel;
	SelectionKey key;
	final ReadyListener readyListener;
	final InetSocketAddress socketAddress;

	public UDPLatencyTest(Poller poller, DatagramChannel channel, ReadyListener readyListener) throws IOException
	{
		super(ByteBuffer.allocateDirect(10240), ByteBuffer.allocateDirect(10240), channel.socket().getSendBufferSize(), false, Logger.getLogger("TestAC"));
		// initialize socketBuffer
		this.poller = poller;
		this.channel = channel;
		this.readyListener = readyListener;
		this.socketAddress = (InetSocketAddress)channel.socket().getRemoteSocketAddress();
	}

	/* (non-Javadoc)
	 * @see com.cosylab.jam.io.PollEvents#registeredNotify(java.nio.channels.SelectionKey, java.lang.Throwable)
	 */
	@Override
	public void registeredNotify(SelectionKey key,
			Throwable registrationException) {
		setSenderThread();
		this.key = key;
		if (readyListener != null)
			readyListener.ready(this);
	}

	/* (non-Javadoc)
	 * @see com.cosylab.jam.io.PollEvents#pollNotify(java.nio.channels.SelectionKey)
	 */
	@Override
	public void pollNotify(SelectionKey key) throws IOException {
		if (key.isReadable())
			processRead();
		if (key.isWritable())
			processWrite();
		if (key.isConnectable()) {
			//channel.finishConnect();
			key.interestOps(SelectionKey.OP_READ);
		}
	}

	@Override
	public int read(ByteBuffer dst) throws IOException {
		if (channel.isConnected())
			return channel.read(dst);
		else
		{
			int p = dst.position();
			InetSocketAddress isa = (InetSocketAddress)channel.receive(dst);
			if (isa == null)
				return 0;
			sendTo = isa;
			return dst.position() - p;
		}
	}

	/* (non-Javadoc)
	 * @see com.cosylab.jam.io.AbstractCodec#invalidDataStreamHandler()
	 */
	@Override
	public void invalidDataStreamHandler() {
		// for TCP we close
		try {
			close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/* (non-Javadoc)
	 * @see com.cosylab.jam.io.AbstractCodec#getLastReadBufferSocketAddress()
	 */
	@Override
	public InetSocketAddress getLastReadBufferSocketAddress() {
		return socketAddress;
	}

	@Override
	public void close() throws IOException {
		channel.close();
	}

	@Override
	public boolean isOpen() {
		return channel.isOpen();
	}

	@Override
	public int write(ByteBuffer src) throws IOException {
		if (channel.isConnected())
			return channel.write(src);
		else
		{
			int p = src.position();
			channel.send(src, sendTo);
			return src.position() - p;
		}
	}

	final static public TransportSender sender = new TransportSender(){

		/* (non-Javadoc)
		 * @see org.epics.ca.client.Lockable#lock()
		 */
		@Override
		public void lock() {
			// TODO Auto-generated method stub
			
		}

		/* (non-Javadoc)
		 * @see org.epics.ca.client.Lockable#unlock()
		 */
		@Override
		public void unlock() {
			// TODO Auto-generated method stub
			
		}
		
		int c = 0;
		long avg = 0;

		long lastTime = 0;
		/* (non-Javadoc)
		 * @see org.epics.ca.impl.remote.TransportSender#send(java.nio.ByteBuffer, org.epics.ca.impl.remote.TransportSendControl)
		 */
		@Override
		public void send(ByteBuffer buffer, TransportSendControl control) {
			long t = System.nanoTime();
			avg += t-lastTime;
			int BIN = 100000;
			if (c++ % BIN == 0)
			{
				System.out.println(avg/(double)BIN);
				avg = 0;
			}
			lastTime = t;
			control.ensureBuffer(CAConstants.CA_MESSAGE_HEADER_SIZE);
			buffer.put(CAConstants.CA_MAGIC);
			buffer.put(CAConstants.CA_VERSION);
			buffer.put((byte)0x81);		
			buffer.put((byte)0);		
			buffer.putInt(0);		
		}
	
	};
	
	@Override
	public void processControlMessage() {
		//System.out.println("processControlMessage():" + command);
		//if (channel.isConnected() || sendTo != null)
		enqueueSendRequest(sender,CAConstants.CA_MESSAGE_HEADER_SIZE);
	}

	@Override
	public void processApplicationMessage() throws IOException {
		System.out.println("processApplicationMessage():" + command);
	}

	@Override
	public void readPollOne() throws IOException {
		poller.pollOne();
	}

	@Override
	public void writePollOne() throws IOException {
		poller.pollOne();
	}

	@Override
	protected void sendBufferFull(int tries) throws IOException {
		writeOpReady = false;
		writeMode = WriteMode.WAIT_FOR_READY_SIGNAL;
		this.writePollOne();
		writeMode = WriteMode.PROCESS_SEND_QUEUE;
	}

	@Override
	public void scheduleSend() {
		key.interestOps(SelectionKey.OP_WRITE);	// TODO allow read?
	}

	@Override
	public void sendCompleted() {
		key.interestOps(SelectionKey.OP_READ);
	}

	@Override
	public void cachedSerialize(Field field, ByteBuffer buffer) {
		// no cache
		field.serialize(buffer, this);
	}


	
	/**
	 * @param args
	 */
	public static void main(String[] args) throws Throwable {
		
		final PollerImpl poller = new PollerImpl();
		poller.start();

		DatagramChannel serverSocket = DatagramChannel.open();
		serverSocket.socket().bind(new InetSocketAddress(1234));
		serverSocket.configureBlocking(false);
		poller.add(serverSocket, new UDPLatencyTest(poller, serverSocket, null), SelectionKey.OP_READ);

		
		DatagramChannel clientSocket = DatagramChannel.open();
		clientSocket.configureBlocking(false);
		UDPLatencyTest ult = new UDPLatencyTest(poller, clientSocket, null);
		poller.add(clientSocket, ult, SelectionKey.OP_READ);
		clientSocket.connect(new InetSocketAddress("192.168.1.102", 1234));
		Thread.sleep(1000);
		ult.enqueueSendRequest(sender, CAConstants.CA_MESSAGE_HEADER_SIZE);
	}

}
