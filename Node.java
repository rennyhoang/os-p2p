import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;

public class Node {
    String address;
    Integer portNumber;
    ArrayList<SimpleEntry<String, Integer>> neighborList = new ArrayList<>();

    public Node(String address, Integer portNumber) {
        this.address = address;
        this.portNumber = portNumber;
    }

    public void startNode() {
        // create a client socket
        try {
            // connect to bootstrap and get neighbor
            Socket bootstrapSocket = new Socket(address, portNumber);
            BufferedReader input = new BufferedReader(new InputStreamReader(bootstrapSocket.getInputStream()));
            String neighborAddress = input.readLine();
            Integer neighborPort = Integer.parseInt(input.readLine());
            bootstrapSocket.close();

            // add neighbor to neighborList
            neighborList.add(new SimpleEntry<>(neighborAddress, neighborPort));

            // tell neighbor to output list of neighbors
            System.out.println(neighborList.toString());
            Socket neighborSocket = new Socket(neighborAddress, neighborPort);
            neighborSocket.close();

            // become a server
            ServerSocket serverSocket = new ServerSocket(8090);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                // output list of neighbors
                System.out.println(neighborList.toString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args){
        Node node = new Node("127.0.0.1", 8080);
        node.startNode();
    }
}
