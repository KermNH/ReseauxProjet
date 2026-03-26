import java.util.*;

public class PlayerVSComputer {
    private int attemptsLeft;
    private String[] secretCombination;
    private boolean isGameOver = false;
    private static final String[] COLORS = {"RED", "BLUE", "GREEN", "YELLOW", "ORANGE"};

    public PlayerVSComputer(int maxAttempts) {
        this.attemptsLeft = maxAttempts;
        this.secretCombination = generateCombination();
        // Debug serveur
        System.out.println("Partie créée. Solution : " + Arrays.toString(secretCombination));
    }
    private String[] generateCombination() {
        Random random = new Random();
        String[] combo = new String[4];
        for (int i = 0; i < 4; i++) {
            combo[i] = COLORS[random.nextInt(COLORS.length)];
        }
        return combo;
    }

    public String handleGuess(String[] guess) {
        if (isGameOver) return "GG|ERROR|La partie est déjà terminée.";

        int bienPlaces = 0;
        int malPlaces = 0;
        boolean[] secretUsed = new boolean[4];
        boolean[] guessUsed = new boolean[4];
        //Bien Placés
        for (int i = 0; i < 4; i++) {
            if (guess[i].equalsIgnoreCase(secretCombination[i])) {
                bienPlaces++;
                secretUsed[i] = true;
                guessUsed[i] = true;
            }
        }
        //Mal Placés
        for (int i = 0; i < 4; i++) {
            if (!guessUsed[i]) {
                for (int j = 0; j < 4; j++) {
                    if (!secretUsed[j] && guess[i].equalsIgnoreCase(secretCombination[j])) {
                        malPlaces++;
                        secretUsed[j] = true;
                        break;
                    }
                }
            }
        }
        attemptsLeft--;
        if (bienPlaces == 4) {
            isGameOver = true;
            return "GG|WIN|Félicitations!";
        } else if (attemptsLeft <= 0) {
            isGameOver = true;
            return "GG|LOSE|" + String.join("-", secretCombination);
        } else {
            // Retourne le format demandé : GG|FEEDBACK|bien_places|mal_places
            return "GG|FEEDBACK|" + bienPlaces + "|" + malPlaces;
        }
    }

    public int getAttemptsLeft() { return attemptsLeft; }
    public boolean isGameOver() { return isGameOver; }
}