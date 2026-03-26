import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;


//Un client manager créé par client, ils fonctionnent en parralèle sur des threads différents
// gerent les interactions avec les clients puisque le serveur doit juste coordonner 

//interface Runnable permet l'utilisation d'instances de cette classe dans des threads
public class ClientManager implements Runnable {
    private Socket socket;
    private BufferedReader in;  // utilisé pour recevoir les messages
    private PrintWriter out;  // utilisé pour envoyer les messages
    private String playerName;

    public ClientManager(Socket socket) {
        this.socket = socket;
    }

    // run vient avec l'interface Runnable, est executé quand le thread démare
    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            String message;
            while ((message = in.readLine()) != null) {
                processMessage(message);
            }
        } catch (IOException e) {
            System.out.println("Client disconnected unexpectedly."); // pour detecter une deconnexion
        } finally {}
    }

    private void processMessage(String message) {
        String[] parts = message.split("\\|");
        if (parts.length < 2 || !parts[0].equals("GG")) return; //si le message est pas du bon format on ignore

        String command = parts[1]; // comme c'est la deuxieme partie la plus importante on regarde que celle la pour le moment

        //gestion des commandes client, pourrait etre pertinent d'ajouter des vérifications sur les arguments 
        // genre pas de noms vides, des int là ou il faut etc
        // et envoyer des messages d'erreur si requete pas possible (genre room pleine)
        switch (command) {
            case "CONNECT":
                this.playerName = parts[2];
                TCPServer.activeClients.put(this.playerName, this); //ajoute le joueur à la liste des clients actifs
                sendMessage("GG|CONNECTED|" + this.playerName);
                break;
            case "CREATE_ROOM":
                String roomName = parts[2];
                int maxPlayers = Integer.parseInt(parts[3]);
                int maxAttempts = Integer.parseInt(parts[4]);
                TCPServer.activeRooms.put(roomName, new GameRoom(roomName, this.playerName, maxPlayers, maxAttempts)); //création de la salle, l'admin est assigné automatiquement
                sendMessage("GG|ROOM_CREATED|" + roomName);
                break;
            case "LIST_ROOMS":
                String roomList = String.join(",", TCPServer.activeRooms.keySet());
                sendMessage("GG|ROOM_LIST|" + roomList);
                break;
            case "JOIN_ROOM":
                GameRoom roomToJoin = TCPServer.activeRooms.get(parts[2]);
                if (roomToJoin != null && roomToJoin.addPlayer(this.playerName))
                {
                    sendMessage("GG|JOINED_ROOM|" + roomToJoin.roomName + "|" + roomToJoin.getPlayersListStr());
                }
                break;
            case "LEAVE_ROOM":
                GameRoom roomToLeave = TCPServer.activeRooms.get(parts[2]);
                if (roomToLeave != null )
                {
                    roomToLeave.removePlayer(this.playerName);
                    sendMessage("GG|LEAVE_ROOM|"+roomToLeave.roomName +" :A BIEN ÉTÉ QUITTÉ");
                }
                break;
            default:
                System.out.println("Unknown command: " + command);
        }
    }

    private void sendMessage(String msg) {
        out.println(msg);
    }
}
