import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class PeerHandler implements Runnable {
    private Socket peerSocket;
    private BufferedReader in;
    private PrintWriter out;

    public PeerHandler(Socket socket) {
        this.peerSocket = socket;
    }
    
    @Override
    public void run() {
        try {
            // Setup streams similar to ClientManager [cite: 13, 14]
            in = new BufferedReader(new InputStreamReader(peerSocket.getInputStream()));
            out = new PrintWriter(peerSocket.getOutputStream(), true);

            String message;
            while ((message = in.readLine()) != null) {
                // Logic for handling peer-to-peer data
                System.out.println("\n[P2P MESSAGE]: " + message);
            }
        } catch (IOException e) {
            System.out.println("Peer connection closed."); 
        } finally {
            try {
                peerSocket.close();
            } catch (IOException e) {
                e.printStackTrace(); 
            }
        }
    }
    
}
