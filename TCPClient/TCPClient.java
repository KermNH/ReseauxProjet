import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;



public class TCPClient {
    private static final String SERVER_IP = "127.0.0.1";
    private static final int SERVER_PORT = 9090; //[cite: 41]
    
    // Store active P2P connections
    private static ConcurrentHashMap<String, Socket> peerConnections = new ConcurrentHashMap<>();
    private static int myP2PPort;

    public static void main(String[] args) {
        // 1. Start a P2P Server Socket on a dynamic port (0 means auto-assign)
        try {
            ServerSocket p2pServer = new ServerSocket(0);
            myP2PPort = p2pServer.getLocalPort();
            System.out.println("Listening for P2P connections on port: " + myP2PPort);
            
            // Thread to accept incoming P2P connections
            new Thread(() -> {
                while (true) {
                    try {
                        Socket peerSocket = p2pServer.accept();
                        
                        System.out.println("\n[P2P] New peer connected from " + peerSocket.getRemoteSocketAddress());
                        PeerHandler handler = new PeerHandler(peerSocket);
                        new Thread(handler).start();
                        // TODO: You would start a new thread here to handle P2P messages (similar to ClientManager)
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        } catch (IOException e) {
            System.err.println("Could not start P2P server: " + e.getMessage());
            return;
        }

        // 2. Standard Server Connection
        try (Socket socket = new Socket(SERVER_IP, SERVER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("Connected to Guess Game Server.");
            
            // Thread to receive responses from the central server 
            Thread listenerThread = new Thread(() -> {
                try {
                    String serverResponse;
                    while ((serverResponse = in.readLine()) != null) {
                        System.out.println("\n[SERVER]: " + serverResponse); //[cite: 43]
                        processMessage(serverResponse);
                    }
                } catch (IOException e) {
                    System.out.println("Connection to server lost."); //[cite: 44]
                }
            });
            listenerThread.start(); //[cite: 45]

            // Tell user to include the P2P port when connecting
            System.out.println("Enter command to connect (e.g., GG|CONNECT|Alice|" + myP2PPort + "):");
            while (true) {
                String input = scanner.nextLine();
                out.println(input); //[cite: 46]
            }

        } catch (IOException e) {
            System.err.println("Could not connect to server: " + e.getMessage()); //[cite: 47]
        }
    }

    private static void processMessage(String message) {
        String[] parts = message.split("\\|"); //[cite: 48]
        if (parts.length < 2 || !parts[0].equals("GG")) return; //[cite: 48]

        String command = parts[1];
        switch (command) {
            case "CONNECT_PEER":
                // The server told us to connect to a new peer in the room
                String peerName = parts[2];
                String peerIp = parts[3];
                int peerPort = Integer.parseInt(parts[4]);
                System.out.println("[DEBUG] Attempting P2P connection to " + peerName + " at " + peerIp + ":" + peerPort);
                connectToPeer(peerName, peerIp, peerPort);
                break;
            default:
                // Expected server responses like JOINED_ROOM, ROOM_CREATED, etc.
                break; 
        }
    }

    private static void connectToPeer(String peerName, String ip, int port) {
        try {
            Socket peerSocket = new Socket(ip, port);
            peerConnections.put(peerName, peerSocket);
            System.out.println("[P2P] Successfully connected to peer: " + peerName);
        } catch (IOException e) {
            System.err.println("[P2P] Failed to connect to peer " + peerName + ": " + e.getMessage());
        }
    }


}