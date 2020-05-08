import java.io.*;
import java.net.Inet4Address;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.Scanner;
import java.util.StringTokenizer;

public class ServerSession implements Runnable {

    private Socket controlSocket;
    private Socket DATA_SOCKET;

    private Scanner controlScanner;
    private PrintWriter controlWriter;

    private PrintWriter DATA_Writer;

    private String port_ip;
    private int port_port;

    private User user;

    static boolean END = false;

    private String currentDir;

    private boolean start_dir = false;


    private TransferType transferType;


    ServerSession(Socket controlSocket, User user) {
        super();
        this.controlSocket = controlSocket;
        try {

            controlScanner = new Scanner(controlSocket.getInputStream());
            controlWriter = new PrintWriter(controlSocket.getOutputStream(), true);

        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalStateException("Problem getting control sockets:" + e);
        }
        this.user = user;
    }


    @Override
    public void run() {
        controlWriter.println("220 OK");
        String cmd;
        cmd = controlScanner.next();

        while (cmd.equals("QUIT") || !END) {
            System.out.println();
            System.out.print("Received command from client " + user.getUserName() + "> " + cmd + " ");


            switch (cmd) {
                case "USER":
                    do_user();
                    break;
                case "MKD":
                    do_MKD();
                    break;
                case "DELE":
                    do_DELE();
                    break;
                case "RMD":
                    String name_dir = controlScanner.next();
                    System.out.println(name_dir);
                    if (do_RMD(currentDir + File.separator + name_dir)) {
                        controlWriter.println("250 Directory is deleted");
                        controlWriter.flush();
                    } else {
                        controlWriter.println("550 RMD Error");
                        controlWriter.flush();
                    }
                    break;
                case "CWD":
                    do_CWD();
                    break;
                case "CDUP":
                    do_CDUP();
                    break;
                case "PORT":
                    String ip_port = controlScanner.next();
                    System.out.println(ip_port);
                    do_port(ip_port);
                    break;
                case "RETR":
                    do_retr();
                    break;
                case "STOR":
                    do_stor();
                    break;
                case "QUIT":
                    do_quit();
                    return;
                case "SYST":
                    controlWriter.println("215 Windows_NT");
                    controlWriter.flush();
                    break;
                case "NLST":
                    do_NLST();
                    break;
                case "TYPE":
                    do_TYPE();
                    break;
                case "PWD":
                    if (!start_dir)
                        currentDir = ".";
                    controlWriter.println("257 " + "\"" + currentDir + "\"" + " is your current location");
                    controlWriter.flush();
                    break;
                case "LIST":
                    try {
                        do_list();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                default:
                    controlWriter.println("501 Unknown command");
            }
            cmd = controlScanner.next();

        }
        System.out.println("SERVER OFF");
        closeClient();
    }

    private void do_TYPE() {
        String type = controlScanner.next();

        if (type.equals("A")) {
            transferType = TransferType.ASKII;
            controlWriter.println("200 Type is ASKII");
        } else if (type.equals("I")) {
            transferType = TransferType.BINARY;
            controlWriter.println("200 Type is binary");
        }
        controlWriter.flush();
    }

    private void do_MKD() {
        String name_dir = controlScanner.next();
        System.out.println(name_dir);
        if (name_dir != null && name_dir.matches("^[a-zA-Z0-9]+$")) {
            File creating_dir = new File(currentDir + File.separator + name_dir);
            if (creating_dir.mkdir()) {
                controlWriter.println("250 Directory successfully created");
            } else controlWriter.println("550 Failed to create new directory");
        } else controlWriter.println("550 Invalid name");
        controlWriter.flush();
    }

    private void do_DELE() {
        String file_name = currentDir + File.separator + controlScanner.next();
        System.out.println(file_name);
        File file = new File(file_name);
        if (file.exists() && file.isFile()) {
            file.delete();
            controlWriter.println("250 File is deleted");
            controlWriter.flush();
        } else controlWriter.println("550 RMD Error");
        controlWriter.flush();
    }

    private boolean do_RMD(String dir) {
        File file = new File(dir);
        if (file.exists() && file.isDirectory()) {

            if (file.list().length == 0) {
                file.delete();

            } else {
                Path currentPath = Paths.get(dir);
                File[] dirList = currentPath.toFile().listFiles();

                for (File f : dirList) {
                    if (f.isFile()) f.delete();
                    else do_RMD(dir + File.separator + f.getName());
                }
                file.delete();
            }
            return true;
        } else return false;
    }

    private void do_CDUP() {  //        C:\\Users\\Kir\\Desktop\\testBD\\terra
        String new_dir = currentDir;

        char[] char_array = new_dir.toCharArray();

        int i = char_array.length - 1;
        try {
            while (char_array[i] != '\\' && char_array[i] != ':') {
                char_array[i] = ' ';
                --i;
            }
        } catch (ArrayIndexOutOfBoundsException ex) {
            currentDir = ".";
            controlWriter.println("250 CDUP succesfull (default root)");
            controlWriter.flush();
            return;
        }
        char_array[i] = ' ';

        int count = 0;
        for (char c : char_array) {
            if (c == ' ')
                count++;
        }

        char[] result = new char[char_array.length - count];
        if (result.length >= 0) System.arraycopy(char_array, 0, result, 0, result.length);

        File file = new File(String.valueOf(result));
        if (file.exists() && file.isDirectory()) {
            currentDir = new String(result);

            controlWriter.println("250 CDUP command succsesful");
            controlWriter.flush();

        } else controlWriter.println("550 CDUP error");

        controlWriter.flush();

    }

    // example for start dir -  C:\\Users\\Kir\\Desktop\\testBD\\terra
    // default dir is "."
    private void do_CWD() {
        String name_dir = controlScanner.next();
        System.out.println(name_dir);

        if (!start_dir) { // was set start dir
            File file = new File(name_dir);

            if (file.exists() && file.isDirectory()) {
                currentDir = name_dir;

                controlWriter.println("250 CWD command succsesful");
                controlWriter.flush();

            } else controlWriter.println("550 CWD error");
            start_dir = true;
        } else {
            String new_dir = currentDir + File.separator + name_dir;
            File file = new File(new_dir);

            if (file.exists() && file.isDirectory()) {
                currentDir = new_dir;

                controlWriter.println("250 CWD command succsesful");
                controlWriter.flush();
            } else controlWriter.println("550 CWD error");

        }
        controlWriter.flush();
    }

    private void do_user() {
        String login = controlScanner.next();
        System.out.println(login);

        user.setUserName(login);
        controlWriter.println("331 Password required for " + login);
        controlWriter.flush();

        controlScanner.next(); // PASS <password>
        String password = controlScanner.next();
        user.setPassword(password);
        controlWriter.println("230 Logged on");
        controlWriter.flush();

        System.out.println("Client " + user.getUserName() + " registered! ");


    }

    public void do_port(String str) {
        try {
            StringTokenizer st = new StringTokenizer(str, ",");
            String[] strArray = new String[st.countTokens()];
            int i = 0;
            char[] tem;
            while (st.hasMoreTokens()) {
                strArray[i] = st.nextToken();
                tem = strArray[i].toCharArray();
                for (int j = 0; j < strArray[i].length(); j++) {
                    if (!Character.isDigit(tem[j])) {
                        controlWriter.println("530 wrong format");
                        controlWriter.flush();
                        return;
                    }
                }
                i++;
            }

            port_ip = strArray[0] + "." + strArray[1] + "." + strArray[2] + "." + strArray[3];
            port_port = Integer.parseInt(strArray[4]) * 256 + Integer.parseInt(strArray[5]);


            try {

                DATA_SOCKET = new Socket(Inet4Address.getByName(port_ip), port_port);

            } catch (Exception e) {
                controlWriter.println("425 fail to start port mode");
                controlWriter.flush();
                DATA_SOCKET.close();
                return;
            }
            controlWriter.println("200 PORT command successful.");
            controlWriter.flush();

        } catch (Exception e) {
            try {
                DATA_SOCKET.close();
            } catch (IOException e1) {
            }
        }
    }

    private void do_retr() {
        String file_name = controlScanner.next();
        System.out.println(file_name);
        File f = new File(currentDir + File.separator + file_name);


        if (!f.exists()) {
            controlWriter.println("550 File does not exist");
        } else {

            if (transferType == TransferType.BINARY) {
                BufferedOutputStream bufferedOutputStream = null;
                BufferedInputStream bufferedInputStream = null;

                controlWriter.println("150 Opening binary mode data connection for requested file " + f.getName());

                try {
                    bufferedOutputStream = new BufferedOutputStream(DATA_SOCKET.getOutputStream());
                    bufferedInputStream = new BufferedInputStream(new FileInputStream(f));
                } catch (Exception e) {
                    e.printStackTrace();
                }

                System.out.println("Starting file transmission");

                byte[] buf = new byte[1024];
                int l;
                try {
                    while ((l = bufferedInputStream.read(buf, 0, 1024)) != -1) {
                        bufferedOutputStream.write(buf, 0, l);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

                try {
                    bufferedInputStream.close();
                    bufferedOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                System.out.println("Completed file transmission of " + f.getName());

                controlWriter.println("226 File transfer successful");

            } else if (transferType == TransferType.ASKII) {
                controlWriter.println("150 Opening ASCII mode data connection for requested file " + f.getName());

                BufferedReader bufferedReader = null;
                PrintWriter printWriter = null;

                try {
                    bufferedReader = new BufferedReader(new FileReader(f));
                    printWriter = new PrintWriter(DATA_SOCKET.getOutputStream(), true);

                } catch (IOException e) {
                    e.printStackTrace();
                }

                String s;

                try {
                    while ((s = bufferedReader.readLine()) != null) {
                        printWriter.println(s);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

                try {
                    bufferedReader.close();
                    printWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                controlWriter.println("226 File transfer successful");
            }
        }
    }

    private void do_quit() {
        controlWriter.println("221 Closing connection");
        closeClient();
    }

    private void do_stor() {
        String file_name = controlScanner.next();
        System.out.println(file_name);

        if (file_name == null)
            controlWriter.println("501 No filename given");

        else {
            File f = new File(currentDir + File.separator + file_name);

            if (transferType == TransferType.ASKII) {
                controlWriter.println("150 Opening ASCII mode data connection for requested file " + f.getName());

                BufferedReader bufferedReader = null;
                PrintWriter printWriter = null;

                try {
                    bufferedReader = new BufferedReader(new InputStreamReader(DATA_SOCKET.getInputStream()));
                    printWriter = new PrintWriter(new FileOutputStream(f), true);
                } catch (IOException e) {
                    System.out.println("Could not create file streams");
                }


                String string;
                try {
                    while ((string = bufferedReader.readLine()) != null) {
                        printWriter.println(string);
                        printWriter.flush();
                    }
                } catch (IOException e) {
                    System.out.println("Could not read from buff");
                }
                try {
                    printWriter.close();
                    bufferedReader.close();
                } catch (IOException e) {
                    System.out.println("Could not close file streams");
                }

            } else if (transferType == TransferType.BINARY) {

                BufferedOutputStream bufferedOutputStream = null;
                BufferedInputStream bufferedInputStream = null;

                controlWriter.println("150 Opening binary mode data connection for requested file " + f.getName());

                try {
                    bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(f));
                    bufferedInputStream = new BufferedInputStream(DATA_SOCKET.getInputStream());
                } catch (Exception e) {
                    e.printStackTrace();
                }

                System.out.println("Start receiving file " + f.getName());

                byte[] buf = new byte[1024];
                int l;
                try {
                    while ((l = bufferedInputStream.read(buf, 0, 1024)) != -1) {
                        bufferedOutputStream.write(buf, 0, l);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

                try {
                    bufferedInputStream.close();
                    bufferedOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                controlWriter.println("226 File transfer successful. Closing data connection.");

            }


        }

    }


    private void do_list() throws IOException {

        controlWriter.println("150 Accepted data connection");
        controlWriter.flush();

        display();
    }

    private void do_NLST() {
        controlWriter.println("150 Accepted data connection");
        controlWriter.flush();

        Path currentPath = Paths.get(currentDir);
        File[] dirList = currentPath.toFile().listFiles();

        try {
            DATA_Writer = new PrintWriter(DATA_SOCKET.getOutputStream(), true);
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        for (File f : dirList) {
            DATA_Writer.write(" " + f.getName());
            DATA_Writer.write("\r\n");
            DATA_Writer.flush();
        }

        controlWriter.println("226 Transfer complete.");
        controlWriter.flush();

        try {
            DATA_Writer.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }


    private static String parse_creationTime(String str) { // 2020-05-05T21:51:05.499049Z   DEC 05 05 21:51
        String s = str.substring(0, 16);

        StringTokenizer st = new StringTokenizer(s, "-T:");
        String[] strArray = new String[st.countTokens()];
        int i = 0;
        while (st.hasMoreTokens()) {
            strArray[i] = st.nextToken();
            i++;
        }

        StringBuilder stringBuilder = new StringBuilder();

        switch (strArray[1]) {
            case "01":
                stringBuilder.append(" JAN");
                break;
            case "02":
                stringBuilder.append(" FEB");
                break;
            case "03":
                stringBuilder.append(" MAR");
                break;
            case "04":
                stringBuilder.append(" APR");
                break;
            case "05":
                stringBuilder.append(" MAY");
                break;
            case "06":
                stringBuilder.append(" JUN");
                break;
            case "07":
                stringBuilder.append(" JUL");
                break;
            case "08":
                stringBuilder.append(" AUG");
                break;
            case "09":
                stringBuilder.append(" SEPT");
                break;
            case "10":
                stringBuilder.append(" OCT");
                break;
            case "11":
                stringBuilder.append(" NOV");
                break;
            case "12":
                stringBuilder.append(" DEC");
                break;
            default:
                System.out.println("incorrect month");
        }

        stringBuilder.append(" " + strArray[2]);
        stringBuilder.append(" " + strArray[0]);
        stringBuilder.append(" " + strArray[3]);
        stringBuilder.append(":" + strArray[4]);

        return stringBuilder.toString();
    }


    private void display() throws IOException {
        Path currentPath = Paths.get(currentDir);
        File[] dirList = currentPath.toFile().listFiles();

        BasicFileAttributes attr;


        try {
            DATA_Writer = new PrintWriter(DATA_SOCKET.getOutputStream(), true);
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        for (File f : dirList) {
            Path p_file = Paths.get(f.getAbsolutePath());
            attr = Files.readAttributes(p_file, BasicFileAttributes.class);
            FileTime fileTime = attr.creationTime();

            if (f.isDirectory()) DATA_Writer.write("d");
            else DATA_Writer.write("f");

            DATA_Writer.write("rw-rw-rw- 1 ftp ftp " + f.length() + " " + parse_creationTime(fileTime.toString()) + " " + f.getName());
            DATA_Writer.write("\r\n");
            DATA_Writer.flush();
        }


        DATA_Writer.write("end of files\r\n");
        DATA_Writer.flush();

        try {
            DATA_Writer.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        controlWriter.println("226 Transfer complete.");
        controlWriter.flush();
    }

    private void closeClient() {

        System.out.println("Session ended for " + user.getUserName());
        try {

            DATA_Writer.close();
            DATA_SOCKET.close();

            controlWriter.close();
            controlScanner.close();
            controlSocket.close();

            for (Map.Entry<User, Socket> entry : Server.clients.entrySet()) {
                if (entry.getKey().getConnectionId().equals(user.getConnectionId())) {
                    Server.clients.remove(entry.getKey());
                }
            }

        } catch (IOException e) {
            System.out.println(("Could not close control or data sockets"));
            e.printStackTrace();
        }
    }

}