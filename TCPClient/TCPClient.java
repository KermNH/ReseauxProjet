import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class TCPClient 
{
    private static final String SERVER_IP = "127.0.0.1";
    private static final int SERVER_PORT = 9090;
    
    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_IP, SERVER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("Connected to Guess Game Server.");
            
            // Thread pour recevoir les reponses du serveur sans bloquer le client
            Thread listenerThread = new Thread(() -> {
                try {
                    String serverResponse;
                    while ((serverResponse = in.readLine()) != null) {
                        System.out.println("\n[SERVER]: " + serverResponse);
                        processMessage(serverResponse);
                    }
                } catch (IOException e) {
                    System.out.println("Connection to server lost.");
                }
            });
            listenerThread.start();

            System.out.println("Enter commands (e.g., GG|CONNECT|Alice):");
            while (true) {
                String input = scanner.nextLine();
                out.println(input);
            }

        } catch (IOException e) {
            System.err.println("Could not connect to server: " + e.getMessage());
        }
    }

    //gestion des messages recus
    private static void processMessage(String message) {
        String[] parts = message.split("\\|");
        if (parts.length < 2 || !parts[0].equals("GG")) return;

        String command = parts[1];

        //pour le moment on a rien, on ajoutera au besoin
        switch (command) {
            default:
                System.out.println("Unknown command: " + command);
        }
    }
}
