package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

public class ChatConnection {

	private final String host;
	private final int port;
	private Socket socket;
	private PrintWriter out;
	private BufferedReader in;
	private Thread readerThread;
	private volatile Consumer<String> onMessage; 
	private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    
	public ChatConnection(String host, int port) {
		this.host = host;
		this.port = port;
	}
	
	public void setOnMessage(Consumer<String> onMessage) {
		this.onMessage = onMessage;
	}
	
	public synchronized void connect(Consumer<String> onMessage) throws IOException {
		if(connected.get()) {
			throw new IllegalStateException("Already connected");
		}
		
		this.onMessage = onMessage;
		socket = new Socket(host, port);
		out = new PrintWriter(socket.getOutputStream(), true);
		in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		connected.set(true);
		closed.set(false);
		
		readerThread = new Thread(() -> {
			try {
				String line;
				while(!closed.get() && (line = in.readLine()) != null) {
					Consumer<String> callback = this.onMessage;
					if(callback != null) { 
						final String msg = line;
						SwingUtilities.invokeLater(() -> callback.accept(msg));
					}
				}
			} catch (IOException e) {
				if(!closed.get()) {
					Consumer<String> callback = this.onMessage;
					if(callback != null)
						SwingUtilities.invokeLater(() -> callback.accept("SYS Disconnected from server."));
				}
			} finally {
				close();
			}
		});
		
		readerThread.setDaemon(true);
		readerThread.start();
	}
	
	public void close() {
		if(closed.getAndSet(true)) return;
		connected.set(false);
		
		try {
			if(in != null) in.close();
		} catch (IOException ignored) {
			
		}
		try {
			if(out != null) out.close();
		} catch (Exception ignored) {
			
		}
		try {
			if(socket != null && !socket.isClosed()) socket.close();
		} catch (IOException ignored) {
			
		}
		
		in = null;
		out = null;
		socket = null;
	}
	
	public void send(String text) {
		PrintWriter w = out;
		if(w != null) w.println(text);
	}
	
}

