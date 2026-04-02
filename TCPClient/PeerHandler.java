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
                            System.out.println("\n[JEU] " + parts[2] + " a défini la combinaison secrète.");
                            break;
                        case "GUESS":
                            System.out.println("\n[JEU] Proposition reçue: " + parts[2] + " " + parts[3] + " " + parts[4] + " " + parts[5]);
                            break;
                        case "FEEDBACK":
                            System.out.println("\n[JEU] Résultat: " + parts[2] + " couleurs correctes, " + parts[3] + " bien placées.");
                            break;
                        case "WINNER":
                            System.out.println("\n[JEU] LA PARTIE EST TERMINÉE ! " + parts[2] + " a gagné !");
                            break;
                        case "NEW_GAME":
                            System.out.println("\n[JEU] Une nouvelle partie a été lancée.");
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
