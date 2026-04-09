import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;


public class TCPClient {
    private   final String SERVER_IP = "127.0.0.1";
    private   final int SERVER_PORT = 9090; //[cite: 41]
    private String[] secretCode = new String[4];
    private   final Queue<String> GuessNameQueue = new LinkedList<>();
    // Store active P2P connections
    public   ConcurrentHashMap<String, Socket> peerConnections = new ConcurrentHashMap<>();
    private   int myP2PPort;
    public   volatile int GameMaster = 0;//-1 = player|0 = gameMaster not yet set|1 = GameMaster
    public   String GameMasterName;
    private   String Name;
    private int maxAttempts = 0;
    private int myCurrentAttempts = 0; // Pour le joueur local
    private ConcurrentHashMap<String, Integer> playerAttempts = new ConcurrentHashMap<>(); // Pour le Host

    public   void main(String[] args) {
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
                        PeerHandler handler = new PeerHandler(peerSocket, this);
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
            System.out.println("Enter command to connect (e.g., GG|CONNECT|Alice)");
            //MESSAGE SENDER
            while (true) {
                String input = scanner.nextLine();
                String[] parts = input.split("\\|"); //[cite: 48]
                if (parts.length < 2 || !parts[0].equals("GG")) return; //[cite: 48]
                String command = parts[1];
                switch(command){
                    case "SECRET_SET":
                        if(parts.length == 6) { // Format attendu: GG|SECRET_SET|Rouge|Bleu|Vert|Jaune
                            this.secretCode[0] = parts[2];
                            this.secretCode[1] = parts[3];
                            this.secretCode[2] = parts[4];
                            this.secretCode[3] = parts[5];
                            this.GameMaster = 1;
                            System.out.println("Combinaison secrète définie ! Vous êtes le Game Master.");

                            // Informer les autres que la partie commence (sans révéler le secret !)
                            for(String key : peerConnections.keySet()){
                                sendMessageToUser(key, "GG|SECRET_SET|" + this.Name);
                            }
                        } else {
                            System.out.println("Usage: GG|SECRET_SET|Couleur1|Couleur2|Couleur3|Couleur4");
                        }
                        break;

                    case "GUESS":
                        if (this.maxAttempts > 0 && this.myCurrentAttempts >= this.maxAttempts) {
                            System.out.println("Dommage ! Vous avez épuisé vos " + maxAttempts + " tentatives.");
                            break;
                        }

                        if(parts.length == 6){
                            if(this.GameMaster == -1){
                                this.myCurrentAttempts++; // Incrémenter localement
                                String msgWithName = addName(input);
                                sendMessageToUser(GameMasterName, msgWithName);
                            }
                        }
                        break;

                    case "FEEDBACK":
                        if(this.GameMaster == 1){
                            String nextPlayer = GuessNameQueue.poll();
                            if(nextPlayer != null){
                                sendMessageToUser(nextPlayer, input);
                            }
                        }
                        break;
                    case "WINNER":
                        if(parts.length == 3){
                            if(GameMaster==1){
                                for(String key : peerConnections.keySet()){
                                    sendMessageToUser(key, input);
                                }
                            }
                        }
                        break;
                    case "CONNECT":
                        input = addPort(input);
                        out.println(input);
                        break;
                    default : 
                        out.println(input);
                        break;
                }
                
            }

        } catch (IOException e) {
            System.err.println("Could not connect to server: " + e.getMessage()); //[cite: 47]
        }
    }

    private   void processMessage(String message) {
        String[] parts = message.split("\\|"); //[cite: 48]
        if (parts.length < 2 || !parts[0].equals("GG")) return; //[cite: 48]

        String command = parts[1];
        switch (command) {
            case "CONNECT_PEER":
                // The server told us to connect to a new peer in the room
                String peerName = parts[2];
                String peerIp = parts[3];
                int peerPort = Integer.parseInt(parts[4]);
                System.out.println("Attempting P2P connection to " + peerName + " at " + peerIp + ":" + peerPort);
                connectToPeer(peerName, peerIp, peerPort);
                break;
            case "PLAYER_KICKED":
                System.out.println("Vous avez été expulsé de la salle.");
                break;
            case "GAME_STARTED":
                // On vérifie qu'on a bien au moins 5 éléments (0 à 4)
                if (parts.length >= 5) {
                    try {
                        // C'est l'index 4 qui contient maxAttempts
                        this.maxAttempts = Integer.parseInt(parts[4]);
                        this.myCurrentAttempts = 0;
                        System.out.println("La partie commence ! Salle: " + parts[2]);
                        System.out.println("Joueurs: " + parts[3]);
                        System.out.println("Tentatives max : " + maxAttempts);
                    } catch (NumberFormatException e) {
                        System.err.println("Erreur format maxAttempts: " + parts[4]);
                        this.maxAttempts = 10; // Valeur de secours
                    }
                }
                break;
            case "SECRET_SET":
                //SetGameMaster(-1);
                this.GameMaster=-1;
                System.out.println(this.GameMaster);
                this.GameMasterName=parts[2];
                System.out.println("\n[JEU] " + parts[2] + " a défini la combinaison secrète.");
                break;
            case "GUESS":
                if(GameMaster==1){
                    System.out.println(parts[6]+" guess: "+parts[2]+", "+parts[3]+", "+parts[4]+", "+parts[5]);
                    GuessNameQueue.add(parts[6]);
                }
                break;
            case "CONNECTED":
                Name=parts[2];
                break;
            default:
                // Expected server responses like JOINED_ROOM, ROOM_CREATED, etc.
                break; 
        }
    }

    public String evaluateGuess(String[] guess, String guessingPlayer) {
        // 1. Incrémenter le compteur du joueur chez le Host
        int current = playerAttempts.getOrDefault(guessingPlayer, 0) + 1;
        playerAttempts.put(guessingPlayer, current);

        int black = 0; // Bien placé
        int white = 0; // Bonne couleur, mauvais endroit

        // On utilise des drapeaux pour ne pas compter deux fois la même bille
        boolean[] secretUsed = new boolean[4];
        boolean[] guessUsed = new boolean[4];

        // Premier passage : les billes bien placées (Noires)
        for (int i = 0; i < 4; i++) {
            if (guess[i].equalsIgnoreCase(secretCode[i])) {
                black++;
                secretUsed[i] = true;
                guessUsed[i] = true;
            }
        }

        // Second passage : les bonnes couleurs mal placées (Blanches)
        for (int i = 0; i < 4; i++) {
            if (!guessUsed[i]) {
                for (int j = 0; j < 4; j++) {
                    if (!secretUsed[j] && guess[i].equalsIgnoreCase(secretCode[j])) {
                        white++;
                        secretUsed[j] = true;
                        break;
                    }
                }
            }
        }

        if (black == 4) {
            return "GG|WINNER|" + guessingPlayer;
        }
        else if (current >= maxAttempts) {
            return "GG|GAMEOVER|" + guessingPlayer;
        }

        else {
            return "GG|FEEDBACK|" + white + "|" + black;
        }
    }
    private   void connectToPeer(String peerName, String ip, int port) {
        try {
            Socket peerSocket = new Socket(ip, port);
            peerConnections.put(peerName, peerSocket);
            System.out.println("[P2P] Successfully connected to peer: " + peerName);
        } catch (IOException e) {
            System.err.println("[P2P] Failed to connect to peer " + peerName + ": " + e.getMessage());
        }
    }

    public   void sendMessageToUser(String targetUserId, String message) {
        Socket socket = peerConnections.get(targetUserId);
        if (socket != null && !socket.isClosed()) {
             try {

                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                out.println(message);
                System.out.println("sending message to :"+ targetUserId);
            } catch (IOException e) {
                System.err.println("[P2P] Error sending message to " + targetUserId + ": " + e.getMessage());
                peerConnections.remove(targetUserId);
            }
        } else {
            System.out.println("[P2P] Could not find active connection for: " + targetUserId);
        }
    }

    private   String addPort(String input){
        StringBuilder sb = new StringBuilder(input);
        sb.append("|");
        sb.append(myP2PPort);
        String result = sb.toString();
        return result;
    }

    private   String addName(String input){
        StringBuilder sb = new StringBuilder(input);
        sb.append("|");
        sb.append(Name);
        String result = sb.toString();
        return result;
    }
    private  void SetGameMaster(int i){
        this.GameMaster=i;
        System.out.println(this.GameMaster);
    }

}
