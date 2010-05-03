/*
 * Copyright (c) 2004 by Cosylab
 *
 * The full license specifying the redistribution, modification, usage and other
 * rights and obligations is included with the distribution of this project in
 * the file "LICENSE-CAJ". If the license is not included visit Cosylab web site,
 * <http://www.cosylab.com>.
 *
 * THIS SOFTWARE IS PROVIDED AS-IS WITHOUT WARRANTY OF ANY KIND, NOT EVEN THE
 * IMPLIED WARRANTY OF MERCHANTABILITY. THE AUTHOR OF THIS SOFTWARE, ASSUMES
 * _NO_ RESPONSIBILITY FOR ANY CONSEQUENCE RESULTING FROM THE USE, MODIFICATION,
 * OR REDISTRIBUTION OF THIS SOFTWARE.
 */

package org.epics.ca.util;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.StringTokenizer;

/**
 * <code>InetAddress</code> utility methods.
 * @author <a href="mailto:matej.sekoranjaATcosylab.com">Matej Sekoranja</a>
 * @version $Id$
 */
public class InetAddressUtil {

	/**
	 * Get broadcast addresses.
	 * @param port port to be added to get socket address.
	 * @return array of broadcast addresses with given port.
	 */
	public static InetSocketAddress[] getBroadcastAddresses(int port) {
		Enumeration<NetworkInterface> nets;
		try {
			nets = NetworkInterface.getNetworkInterfaces();
		} catch (SocketException se) {
			// fallback
			return new InetSocketAddress[] { new InetSocketAddress("255.255.255.255", port) };
		}

		ArrayList<InetSocketAddress> list = new ArrayList<InetSocketAddress>(10);

			while (nets.hasMoreElements())
			{
				NetworkInterface net = nets.nextElement();
				//if (net.isUp())
				{
					List<InterfaceAddress> interfaceAddresses = net.getInterfaceAddresses();
					for (InterfaceAddress addr : interfaceAddresses)
						if (addr.getBroadcast() != null)
						{
							InetSocketAddress isa = new InetSocketAddress(addr.getBroadcast(), port);
							if (!list.contains(isa))
								list.add(isa);
						}
				}
			}
		
		InetSocketAddress[] retVal = new InetSocketAddress[list.size()];
		list.toArray(retVal);
		return retVal;
	}

	/**
	 * Encode address as IPv6 address.
	 * @param buffer byte-buffer where to put encoded data.
	 * @param address address to encode.
	 * @throws RuntimeException
	 */
	public static final void encodeAsIPv6Address(ByteBuffer buffer, InetAddress address) throws RuntimeException {
		if (address instanceof Inet6Address)
			buffer.put(address.getAddress());
		else if (address instanceof Inet4Address)
		{
			// IPv4 compatible IPv6 address
			// first 80-bit are 0
			buffer.putLong(0);
			buffer.putShort((short)0);
			// next 16-bits are 1
			buffer.putShort((short)0xFFFF);
			// following IPv4 address
			buffer.put(address.getAddress());
		}
		else
			throw new RuntimeException("unsupported network addresss: " + address);
	}

	/**
	 * Convert an integer into an IPv4 INET address.
	 * @param addr integer representation of a given address.
	 * @return IPv4 INET address.
	 */
	public static InetAddress intToIPv4Address(int addr) {
		byte[] a = new byte[4];

		a[0] = (byte) ((addr >> 24) & 0xFF);
		a[1] = (byte) ((addr >> 16) & 0xFF);
		a[2] = (byte) ((addr >>  8) & 0xFF);
		a[3] = (byte) ((addr & 0xFF));

		InetAddress res = null;
		try {
			res = InetAddress.getByAddress(a);
		} catch (UnknownHostException e) { /* noop */ }

		return res;
	}

	/** 
	 * Convert an IPv4 INET address to an integer.
	 * @param addr	IPv4 INET address.
	 * @return integer representation of a given address.
	 * @throws IllegalArgumentException if the address is really an IPv6 address
	 */
	public static int ipv4AddressToInt(InetAddress addr) {

		if (addr instanceof Inet6Address)
			throw new IllegalArgumentException("IPv6 address used in IPv4 context");

		byte[] a = addr.getAddress();

		return ((a[0] & 0xFF) << 24)
		     | ((a[1] & 0xFF) << 16)
		   	 | ((a[2] & 0xFF) << 8)
		  	 |  (a[3] & 0xFF);
	}


	/**
	 * Parse space delimited addresss[:port] string and return array of <code>InetSocketAddress</code>.  
	 * @param list	space delimited addresss[:port] string.
	 * @param defaultPort	port take if not specified.
	 * @return	array of <code>InetSocketAddress</code>.
	 */
	public static InetSocketAddress[] getSocketAddressList(String list, int defaultPort) {
		return getSocketAddressList(list, defaultPort, null);
	}

	/**
	 * Parse space delimited addresss[:port] string and return array of <code>InetSocketAddress</code>.  
	 * @param list	space delimited addresss[:port] string.
	 * @param defaultPort	port take if not specified.
	 * @param appendList 	list to be appended.
	 * @return	array of <code>InetSocketAddress</code>.
	 */
	public static InetSocketAddress[] getSocketAddressList(String list, int defaultPort, InetSocketAddress[] appendList)
	{
		ArrayList<InetSocketAddress> al = new ArrayList<InetSocketAddress>();
		
		// parse string
		StringTokenizer st = new StringTokenizer(list);
		while (st.hasMoreTokens())
		{
			int port = defaultPort;
			String address = st.nextToken();

			// check port
			int pos = address.indexOf(':'); 
			if (pos >= 0)
			{
				try {
					port = Integer.parseInt(address.substring(pos + 1));
				}
				catch (NumberFormatException nfe) { /* noop */ }
				
				address = address.substring(0, pos);
			}
			
			// add parsed address
			al.add(new InetSocketAddress(address, port));
		}

		// copy to array
		int appendSize = (appendList == null) ? 0 : appendList.length; 
		InetSocketAddress[] isar = new InetSocketAddress[al.size() + appendSize];
		al.toArray(isar);
		if (appendSize > 0)
			System.arraycopy(appendList, 0, isar, al.size(), appendSize);
		return isar;
	}

}
