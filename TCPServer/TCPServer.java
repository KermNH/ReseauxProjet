import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;



public class TCPServer {

    private static final int PORT = 9090;

    public static ConcurrentHashMap<String, ClientManager> activeClients = new ConcurrentHashMap<>(); //stocke les clients actifs, clé = nom du joueur
    public static ConcurrentHashMap<String, GameRoom> activeRooms = new ConcurrentHashMap<>(); //stocke les salles de jeu actives, clé = nom de la salle

    public static void main(String[] args) 
    {
        System.out.println("Starting Guess Game Server on port " + PORT + "...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) 
        {
            while (true) 
            {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New connection from " + clientSocket.getRemoteSocketAddress());
                
                ClientManager clientManager = new ClientManager(clientSocket);
                new Thread(clientManager).start(); // On commence un nouveau thread pour gérer le nouveau client
            }
        } catch (IOException e) 
        {
            System.err.println("Server exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
