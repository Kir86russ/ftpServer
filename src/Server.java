import java.io.IOException;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;


public class Server {

    static ConcurrentHashMap<User, Socket> clients = new ConcurrentHashMap<>();
    static ServerSocket serverSocket;

    public static void main(String[] args) {

        Thread handlerThread = null;

        try {

            serverSocket = new ServerSocket(25060, 10, Inet4Address.getLocalHost());
            handlerThread = new Thread(new ControlHandler());
            handlerThread.start();

        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Problem creating server control socket: " + e);
        }

        Scanner serverInputScanner = new Scanner(System.in);

        String servCommand = "";

        while (!servCommand.equals("CLOSE")) {
            servCommand = serverInputScanner.nextLine().trim();
        }

        try {
            serverSocket.close();
        } catch (IOException e) {
            System.out.println("Error closing server socket");
            e.printStackTrace();
        }

        try {
            handlerThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


    }

}
