import java.util.ArrayList;
import java.util.List;

public class GameRoom 
{
    String roomName;
    String adminName;
    int maxPlayers;
    int maxAttempts;
    List<String> players;

    String[] secretCode;
   

    public GameRoom(String name, String admin, int maxP, int maxA) 
    {
        this.roomName = name;
        this.adminName = admin;
        this.maxPlayers = maxP;
        this.maxAttempts = maxA;
        this.players = new ArrayList<>();
        this.players.add(adminName);
    }
    
    // le synchronized permet d'éviter les situations de courses avec les threads
    // sinon on risquerait d'ajouter deux joueurs au meme moment et de dépasser la limite de joueurs max par exemple
    // et on veut pas ça

    public synchronized boolean addPlayer(String playerName) 
    {
        if (players.size() < maxPlayers) 
        {
            players.add(playerName);
            return true;
        }
        return false;
    }

    public synchronized void removePlayer(String playerName) 
    {
        players.remove(playerName);
    }
    
    public synchronized String getPlayersListStr() 
    {
        return String.join(",", players);
    }

    public void setSecretCode(String code)
    {
        //il nous a pas dit comment déterminer le code secret de base
    }
}
