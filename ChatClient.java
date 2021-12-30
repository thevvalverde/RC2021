import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;


public class ChatClient {

    // Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();
    // --- Fim das variáveis relacionadas coma interface gráfica

    // Se for necessário adicionar variáveis ao objecto ChatClient, devem
    // ser colocadas aqui

    String server;
    int port;
    Socket clientSocket;

    String start = "";
    MessageReader reader;

    
    // Método a usar para acrescentar uma string à caixa de texto
    // * NÃO MODIFICAR *
    public void printMessage(final String message) {
        chatArea.append(message);
    }

    
    // Construtor
    public ChatClient(String server, int port) throws IOException {

        // Inicialização da interface gráfica --- * NÃO MODIFICAR *
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(chatBox);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.setSize(500, 300);
        frame.setVisible(true);
        chatArea.setEditable(false);
        chatBox.setEditable(true);
        chatBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    newMessage(chatBox.getText());
                } catch (IOException ex) {
                } finally {
                    chatBox.setText("");
                }
            }
        });
        frame.addWindowListener(new WindowAdapter() {
            public void windowOpened(WindowEvent e) {
                chatBox.requestFocusInWindow();
            }
        });
        // --- Fim da inicialização da interface gráfica

        // Se for necessário adicionar código de inicialização ao
        // construtor, deve ser colocado aqui

        this.server = server;
        this.port = port;


        clientSocket = new Socket(server, port);

    }


    // Método invocado sempre que o utilizador insere uma mensagem
    // na caixa de entrada
    public void newMessage(String message) throws IOException {
        // PREENCHER AQUI com código que envia a mensagem ao servidor

        DataOutputStream toServer = new DataOutputStream(clientSocket.getOutputStream());
        message = (message.charAt(0)=='/') ? "/" + message : message;
        toServer.writeBytes(message + '\n'); // Write to server

    }

    
    // Método principal do objecto
    public void run() throws IOException {
        // PREENCHER AQUI

        reader = new MessageReader(this);
        reader.start();

    }
    

    // Instancia o ChatClient e arranca-o invocando o seu método run()
    // * NÃO MODIFICAR *
    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }

    class MessageReader implements Runnable{
        boolean open = true;
        ChatClient client;
        Thread thr;
        String start = "";

        public MessageReader(ChatClient client){
            this.client = client;
        }

        public void start() {
            thr = new Thread(this);
            thr.start();
        }

        public void close() { open = false; }

        public void run(){
            while(open) {
                try {
                    BufferedReader fromServer = new BufferedReader(new InputStreamReader(client.clientSocket.getInputStream()));
                    String tmp = fromServer.readLine();
                    if(tmp==null) {
                        try {
                            Thread.sleep(1000);
                        } catch (Exception e) {
                            System.err.println(e);
                        }
                        frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
                        return;
                    }
                    String[] rcvd = tmp.split(" ", 3);
                    String ans = "";
                    switch(rcvd[0]) {
                        case "MESSAGE":
                            ans += rcvd[1] + ": " + rcvd[2];
                            break;
                        case "NEWNICK":
                            ans += rcvd[1] + " mudou de nome para " + rcvd[2];
                            break;
                        case "OK":
                            ans += "OK";
                            break;
                        case "ERROR":
                            ans += "ERROR";
                            break;
                        case "JOINED":
                            ans += rcvd[1] + " entrou na sala";
                            break;
                        case "LEFT":
                            ans += rcvd[1] + " saiu da sala";
                            break;
                        case "PRIVATE":
                            ans += "(priv) " + rcvd[1] + ": " + rcvd[2];
                            break;
                        case "BYE":
                            ans += "BYE";
                    }
                    client.printMessage(ans + "\n");
                } catch(IOException err) {
                    System.out.println(err.toString());
                    close();
                }
            }
        }
    }

}