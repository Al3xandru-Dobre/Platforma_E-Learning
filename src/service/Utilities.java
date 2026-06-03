package service;
import exception.InvalidString;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public interface Utilities
{

    static boolean containsAtLeast(String word, char character, int x) {
        char[] copie = word.toCharArray();
        int contor = 0;
        for (int i = 0 ; i < word.length(); i++) {
            if(copie[i] == character) contor++;
        }
        return  contor >= x;
    }

    static String readString(String readingMessage) throws IOException{
        final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        System.out.println(readingMessage);
        String input = reader.readLine().trim();
        while(input.isEmpty()){
            System.out.println("Input trebuie sa contina ceva");
            System.out.println(readingMessage);
            input = reader.readLine().trim();
        }
        return input;
    }

    static void checkString(String stringToCheck) {
        if(stringToCheck == null || stringToCheck.isBlank()) throw new InvalidString("Ai introdus nimic ;D");
    }
}
