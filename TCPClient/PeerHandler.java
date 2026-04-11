import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class PeerHandler implements Runnable {
    private Socket peerSocket;
    private TCPClient parentClient;
    private BufferedReader in;
    private PrintWriter out;

    public PeerHandler(Socket socket, TCPClient parent) {
        this.peerSocket = socket;
        this.parentClient = parent;
    }
    
    @Override
    public void run() {
        try {
            // Setup streams similar to ClientManager
            in = new BufferedReader(new InputStreamReader(peerSocket.getInputStream()));
            out = new PrintWriter(peerSocket.getOutputStream(), true);

            String message;
            while ((message = in.readLine()) != null) {
                String[] parts = message.split("\\|");
                if (parts.length >= 2 && parts[0].equals("GG")) {
                    String command = parts[1];

                    switch(command) {
                        case "SECRET_SET":
                            System.out.println("\n[JEU] " + parts[2] + " a défini la combinaison secrète tu est désormais un joueur.");
                            System.out.println("\n[JEU] faite votre guess sur le format GG|GUESS|couleur1|couleur2|couleur3|couleur4  ");
                            parentClient.GameMaster=-1;
                            parentClient.GameMasterName = parts[2];
                            break;
                        case "GUESS":
                            System.out.println("\n[JEU] Proposition reçue de " + parts[6] + ": " + parts[2] + " " + parts[3] + " " + parts[4] + " " + parts[5]);

                            if (parentClient.GameMaster == 1) {
                                String[] playerGuess = {parts[2], parts[3], parts[4], parts[5]};
                                String playerName = parts[6];

                                String response = parentClient.evaluateGuess(playerGuess, playerName);
                                parentClient.sendMessageToUser(playerName, response);
                                if (response.startsWith("GG|WINNER")) {
                                    // Diffuser la fin de partie à tout le monde
                                    for(String peer : parentClient.peerConnections.keySet()) {
                                        parentClient.sendMessageToUser(peer, response);
                                    }
                                }
                            }
                            break;
                        case "FEEDBACK":
                            System.out.println("\n[JEU] Résultat: " + parts[2] + " couleurs dans la combinaison, " + parts[3] + " bien placées.");
                            break;
                        case "WINNER":
                            System.out.println("\n[JEU] LA PARTIE EST TERMINÉE ! " + parts[2] + " a gagné !");
                            break;
                        /*case "NEW_GAME":
                            System.out.println("\n[JEU] Une nouvelle partie a été lancée.");
                            break;*/
                        case "GAMEOVER":
                            System.out.println("\n[JEU] " + parts[2] + " a épuisé toutes ses tentatives !");
                            break;
                    }
                }
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
