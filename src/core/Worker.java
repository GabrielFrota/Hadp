package core;

import java.net.Socket;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Scanner;
import java.util.concurrent.Callable;

public class Worker implements Callable<Integer> {
    
  private class WorkerRemoteImpl extends UnicastRemoteObject implements WorkerRemote {

    private static final long serialVersionUID = 1L;

    public WorkerRemoteImpl() throws RemoteException {
      super();
    }
    
    @Override
    public String getOK() throws RemoteException {
      return "OK";
    }
    
    private String chunk;  
    @Override public int sendMapChunk(String c) throws RemoteException {
      chunk = c;
      System.out.println(chunk);
      return chunk.length();
    }
    
  }

  @Override
  public Integer call() throws Exception {
    try (var sock = new Socket("www.google.com", 80)) {
      System.setProperty("java.rmi.server.hostname", sock.getLocalAddress().getHostAddress());
    }
    var impl = new WorkerRemoteImpl();
    var reg = LocateRegistry.createRegistry(1099);
    var ip = System.getProperty("java.rmi.server.hostname");
    reg.bind(WorkerRemote.NAME, impl);
    System.out.println("RMI Registry is binded to address " + ip + ":1099 exporting WorkerRemote interface.\n"
        + "Type \"quit\" to stop the server and close the JVM.");

    try (var scan = new Scanner(System.in)) {
      while (!scan.nextLine().equals("quit"));
    }
    return 0;
  }

}
