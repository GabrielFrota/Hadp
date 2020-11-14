package exec;

import java.io.File;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;

import interf.MapReduce;
import lib.ClassFileServer;
import lib.CommandLine;
import lib.CommandLine.Command;
import lib.CommandLine.Option;

@Command(name = "core/Master", mixinStandardHelpOptions = false, 
    description = "Master proccess for distributed MapReduce jobs in a cluster.")
class Master implements Callable<Integer> {
  
  private String timestamp = "_" + LocalDateTime.now().toString().replace(".", "_");
  @SuppressWarnings("rawtypes")
  private MapReduce mapRed;
  
  private class MasterRemoteImpl extends UnicastRemoteObject implements MasterRemote {  
    private static final long serialVersionUID = 1L;

    public MasterRemoteImpl() throws RemoteException {
      super(1100);
    }
    
    @Override
    @SuppressWarnings("rawtypes")
    public MapReduce getMapReduceImpl() throws RemoteException {
      return mapRed;
    }
  }
  
  private class MasterClassLoader extends ClassLoader {
    public Class<?> loadClass(String name, byte[] b) {
      return defineClass(name, b, 0, b.length);
    }  
  }
  
  @Option(names = {"-i", "--input"}, description = "Input file path.", 
      paramLabel = "FILE", required = true)
  private File input;

  @Option(names = {"-o", "--output"}, description = "Output file path.", 
      paramLabel = "FILE", required = true)
  private File output;

  @Option(names = {"-w", "--workers"}, description = "Workers IP addresses file path.", 
      paramLabel = "FILE", required = true)
  private File workersFile;
  
  @Option(names = {"-mp", "--mapreduce"}, description = "MapReduce implementation class file path.", 
      paramLabel = "FILE", required = true)
  private File mapReduceFile;
  
  @Option(names = {"-ov", "--overwrite"}, description = "Overwrite splits in Workers.")
  private boolean overwrite;
    
  private final static CommandLine comm = new CommandLine(new Master());
  
  private void printErr(String msg) {
    var str = comm.getColorScheme().errorText(msg).toString();
    comm.getErr().println(str);
  }
  
  private WorkerRemote getWorkerRemote(String ip) 
    throws RemoteException, AccessException, NotBoundException 
  {
    var reg = LocateRegistry.getRegistry(ip);
    return (WorkerRemote) reg.lookup(WorkerRemote.NAME);
  }
  
  private boolean checkExistsCanRead(File f, String param) {
    if (!f.exists() || !f.canRead()) {
      printErr("Invalid " + param + " parameter value. Correct file "
          + "path to an existing file with read permission is required.");
      return false;
    }
    return true;
  }
  
  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  public Integer call() throws Exception {
    var params = Map.of(
      input, "--input", 
      workersFile, "--workers",
      mapReduceFile, "--mp"
    );
    for (var p : params.entrySet()) {
      if (!checkExistsCanRead(p.getKey(), p.getValue()))
        return -1;
    }
    if (output.exists()) 
      output.delete();
    if (!output.createNewFile()) {
      printErr("Output file creation failed.");
      return -1;
    }
    
    var remapperIn = new HashMap<String, String>();
    byte[] b = Files.readAllBytes(mapReduceFile.toPath());
    var cr = new ClassReader(b);
    cr.accept(new ClassVisitor(Opcodes.ASM7) {
      @Override
      public void visit(int version, int access, String name, String signature, 
                        String superName, String[] interfaces) {
        if (!remapperIn.isEmpty()) 
          return;
        var strs = name.split("/");
        strs[strs.length - 1] = timestamp;
        var sj = new StringJoiner("/");
        for (var s : strs) 
          sj.add(s);
        remapperIn.put(name, sj.toString());
      }
    }, 0);
    var cw = new ClassWriter(0);
    var re = new ClassRemapper(cw, new SimpleRemapper(remapperIn));
    cr.accept(re, 0);
    var tempFilePath = remapperIn.values().iterator().next() + ".class";
    var tempFileFullPath = Paths.get("bin/" + tempFilePath);
    byte[] bClazz = cw.toByteArray(); 
    Files.write(tempFileFullPath, bClazz, StandardOpenOption.CREATE);
    var clazzName = tempFilePath.split("\\.")[0].replace("/", ".");
    var clazz = new MasterClassLoader().loadClass(clazzName, bClazz);
    mapRed = (MapReduce) clazz.getDeclaredConstructor().newInstance();
    
    var out = comm.getOut();
    @SuppressWarnings("unused")
    var fileServer = new ClassFileServer(8080, "./bin");  
    try (var sock = new Socket("www.google.com", 80)) {
      var addr = sock.getLocalAddress().getHostAddress();
      System.setProperty("java.rmi.server.hostname", addr);
      System.setProperty("java.rmi.server.codebase", "http://" + addr + ":8080/");
    }
    var impl = new MasterRemoteImpl();
    var reg = LocateRegistry.createRegistry(1100);
    reg.bind(MasterRemote.NAME, impl);
    out.println("RMI Registry is binded to address " 
        + System.getProperty("java.rmi.server.hostname") 
        + ":1100 exporting MasterRemote interface.");
    
    var workers = Files.readAllLines(workersFile.toPath());
    for (var ip : workers) {
      var worker = getWorkerRemote(ip);
      if (!worker.getOK().equals("OK"))
        throw new RuntimeException("Host " + ip + " did not answered properly.");
      mapRed.workers.add(ip);
    }
    mapRed.setInputName(input.getName());
    for (var ip : workers) {
      var worker = getWorkerRemote(ip);
      worker.setMasterIp(System.getProperty("java.rmi.server.hostname"));
      worker.downloadImpl();
    }
    
    var text = mapRed.getInputFormat();
    var splits = text.getSplits(input, mapRed.workers.size());
    int i = 0;
    for (var s : splits) {
      var worker = getWorkerRemote(workers.get(i++));
      boolean exists = worker.exists(mapRed.getInputName());
      if (!exists || overwrite) {
        if (exists) worker.delete(mapRed.getInputName());
        worker.createNewFile(mapRed.getInputName());
        out.println("Sending " + s + " to worker " + worker.getIp());      
        var inStream = Files.newInputStream(s.toPath(), StandardOpenOption.READ);
        byte[] buf = new byte[WorkerRemote.CHUNK_LENGTH];
        worker.initOutputStream(mapRed.getInputName());
        for (int len = inStream.read(buf); len != -1; len = inStream.read(buf)) {
          worker.write(buf, len);
        }
        worker.closeOutputStream();
      }
    }
     
    var pool = ForkJoinPool.commonPool();
    var tasks = new LinkedList<ForkJoinTask<Integer>>();
    for (var ip : workers) {
      var t = pool.submit(() -> {
        var worker = getWorkerRemote(ip);
        worker.doMap();
        return 0;
      });
      tasks.add(t);
    }
    for (var t : tasks) {
      t.get();
    } 
    tasks.clear();
    for (var ip : mapRed.workers) {
      var t = pool.submit(() -> {
        var worker = getWorkerRemote((String) ip);
        worker.gatherChunks(mapRed.workers.indexOf(ip));
        worker.doReduce();
        return 0;
      });
      tasks.add(t);
    }
    for (var t : tasks) {
      t.get();
    }
    
    out.println("FINISHED");
    Files.delete(tempFileFullPath);
    return 0;
  }

  public static void main(String[] args) {
    int exitCode = comm.execute(args);
    System.exit(exitCode);
  }

}
