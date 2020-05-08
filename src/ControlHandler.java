import java.io.IOException;
import java.net.Socket;
import java.util.Random;

public class ControlHandler implements Runnable {

    private static Random random;

    static {
        random = new Random();
    }

    ControlHandler() {
        System.out.println("Control socket started with address " + Server.serverSocket.getInetAddress() +
                " port " + Server.serverSocket.getLocalPort());
    }

    @Override
    public void run() {
        Thread client = null;
        while (true) {
            Socket socket;
            try {
                socket = Server.serverSocket.accept();
            } catch (IOException e) {
                System.out.println("Server is off");
                ServerSession.END = true;
                try {
                    client.join();
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
                break;
            }

            System.out.println("Control socket connection from "
                    + socket.getInetAddress() + " port " + socket.getPort());

            Long connectionId = random.nextLong();

            User user = new User(connectionId);
            Server.clients.put(user, socket);

            client = new Thread(new ServerSession(socket, user));
            client.start();


        }
    }
}
