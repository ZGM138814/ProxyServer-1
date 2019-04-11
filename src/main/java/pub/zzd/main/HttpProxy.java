package pub.zzd.main;
/*************************************
 * 一个基础的代理服务器类
 *************************************
 */
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.ArrayHandler;
import pub.zzd.utils.MyC3P0Utils;
import java.net.*;
import java.io.*;

public class HttpProxy extends Thread {
	static public int CONNECT_RETRIES=5;
	static public int CONNECT_PAUSE=5;
	static public int TIMEOUT=50;
	static public int BUFSIZ=1024;
	static public boolean logging = false;
	static public boolean proxy = false;
	static public OutputStream log=null;
	/**
	 * 传入数据用的Socket
	 */
	protected Socket socket;
	/**
	 * 上级代理服务器，可选
	 */
	static private String parent=null;
	static private int parentPort=-1;
	static public void setParentProxy(String name, int pport) {
		parent=name;
		parentPort=pport;
	}

	static public void setParentProxy(Boolean b) {
		try {
			if (b){
				QueryRunner qr = new QueryRunner(MyC3P0Utils.getDataSource());
				String sql = "select host,port from t_proxy order by rand() limit 1";
				Object[] query = qr.query(sql, new ArrayHandler());
				System.out.println(query[0]+":"+query[1]);
				HttpProxy.setParentProxy(query[0].toString(),Integer.valueOf(query[1].toString()));
			}
		}catch (Exception e){
			e.printStackTrace();
		}

	}

	/**
	 * 在给定Socket上创建一个代理线程。
	 * @param s
	 */
	public HttpProxy(Socket s)
	{
		socket=s;
		start();
	}

	public void writeLog(int c, boolean browser) throws IOException {
		log.write(c);
	}

	public void writeLog(byte[] bytes,int offset, int len, boolean browser) throws IOException {
		for (int i=0;i<len;i++) writeLog((int)bytes[offset+i],browser);
	}


	// 默认情况下，日志信息输出到
	// 标准输出设备
	// 派生类可以覆盖它
	public String processHostName(String url, String host, int port, Socket sock) {
		java.text.DateFormat cal=java.text.DateFormat.getDateTimeInstance();
		System.out.println(cal.format(new java.util.Date()) + " - " + url + " "
				+ sock.getInetAddress()+"\n");
		return host;
	}


	/**
	 * 执行操作的线程
	 */
	@Override
	public void run() {
		String line;
		String host;
		int port=80;
		Socket outbound=null;
		try {
			socket.setSoTimeout(TIMEOUT);
			InputStream is=socket.getInputStream();
			OutputStream os=null;
			try {
				// 获取请求行的内容
				line="";
				host="";
				int state=0;
				boolean space;
				while (true) {
					int c=is.read();
					if (c==-1) break;
					if (logging) writeLog(c,true);
					// 判断指定字符是否为空白字符，空白符包含：空格、tab 键、换行符。
					space=Character.isWhitespace((char)c);
					switch (state) {
						case 0:
							if (space) continue;
							state=1;
						case 1:
							if (space) {
								state=2;
								continue;
							}
							line=line+(char)c;
							break;
						case 2:
							// 跳过多个空白字符
							if (space) continue;
							state=3;
						case 3:
							if (space) {
								state=4;
								// 只取出主机名称部分
								String host0=host;
								int n;
								n=host.indexOf("//");
								if (n!=-1) host=host.substring(n+2);
								n=host.indexOf('/');
								if (n!=-1) host=host.substring(0,n);
								// 分析可能存在的端口号
								n=host.indexOf(":");
								if (n!=-1) {
									port=Integer.parseInt(host.substring(n+1));
									host=host.substring(0,n);
								}
								host=processHostName(host0,host,port,socket);
								if (parent!=null) {
									host=parent;
									port=parentPort;
								}
								int retry=CONNECT_RETRIES;
								while (retry--!=0) {
									try {
										outbound=new Socket(host,port);
										break;
									} catch (Exception e) { }
									// 等待
									Thread.sleep(CONNECT_PAUSE);
								}
								if (outbound==null){
									System.out.println("=================================outbound是空的，重新获取代理服务器");
									HttpProxy.setParentProxy(proxy);
									break;
								}
								outbound.setSoTimeout(TIMEOUT);
								os=outbound.getOutputStream();
								os.write(line.getBytes());
								os.write(' ');
								os.write(host0.getBytes());
								os.write(' ');
								pipe(is,outbound.getInputStream(),os,socket.getOutputStream());
								break;
							}
							host=host+(char)c;
							break;
							default:break;
					}
				}
			}
			catch (IOException e) { }

		} catch (Exception e) { }
		finally {
			try {
				socket.close();
			} catch (Exception e1) {

			}
			try {
				outbound.close();
			} catch (Exception e2) {

			}
		}
	}

	void pipe(InputStream is0, InputStream is1, OutputStream os0,  OutputStream os1) throws Exception {
		try {
			int ir;
			byte bytes[]=new byte[BUFSIZ];
			while (true) {
				try {
					if ((ir=is0.read(bytes))>0) {
						os0.write(bytes,0,ir);
						if (logging) writeLog(bytes,0,ir,true);
					}
					else if (ir<0)
						break;
				} catch (InterruptedIOException e) { }
				try {
					if ((ir=is1.read(bytes))>0) {
						os1.write(bytes,0,ir);
						if (logging) writeLog(bytes,0,ir,false);
					}
					else if (ir<0)
						break;
				} catch (InterruptedIOException e) { }
			}
		} catch (Exception e0) {
			System.out.println("=================================与代理连接中断，设置新的代理服务器");
			HttpProxy.setParentProxy(proxy);
			System.out.println("Pipe异常: " + e0);
		}
	}


	static public void startProxy(int port,Class clobj) {
		ServerSocket ssock;
		Socket sock;
		try {
			ssock=new ServerSocket(port);
			while (true) {
				Class [] sarg = new Class[1];
				Object [] arg= new Object[1];
				sarg[0]=Socket.class;
				try {
					java.lang.reflect.Constructor cons = clobj.getDeclaredConstructor(sarg);
					arg[0]=ssock.accept();
					// 创建HttpProxy或其派生类的实例
					cons.newInstance(arg);
				} catch (Exception e) {
					Socket esock = (Socket)arg[0];
					try { esock.close(); } catch (Exception ec) {}
				}
			}
		} catch (IOException e) {
		}
	}


	/**
	 * 测试用的简单main方法
	 * @param args
	 */
	static public void main(String args[]) {
		HttpProxy.proxy=true;
		HttpProxy.setParentProxy(HttpProxy.proxy);
		System.out.println("在端口8988启动代理服务器");
		HttpProxy.log=System.out;
		HttpProxy.logging=false;
		HttpProxy.startProxy(8988,HttpProxy.class);
	}
}
