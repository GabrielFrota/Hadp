package exec;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import params.TestImpl;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "core/Master", mixinStandardHelpOptions = true, 
    description = "Master proccess for distributed MapReduce jobs in a cluster.")
public class Master implements Callable<Integer> {
  
  @Option(names = {"-i", "--input"}, description = "Input file path.", 
      paramLabel = "FILE", required = true)
  private File input;

  @Option(names = {"-o", "--output"}, description = "Output file path.", 
      paramLabel = "FILE", required = true)
  private File output;

  @Option(names = {"-w", "--workers"}, description = "Workers IP addresses.", 
      paramLabel = "FILE", required = true)
  private File workersFile;
  
  private final List<String> workers = new ArrayList<String>();
  
  @Option(names = {"-ov", "--overwrite"})
  private boolean overwrite;

  private final static CommandLine comm = new CommandLine(new Master());
  
  private void printErr(String msg) {
    var str = comm.getColorScheme().errorText(msg).toString();
    comm.getErr().println(str);
  }
  
  private WorkerRemote getWorkerRemote(String ip) 
    throws RemoteException, AccessException, NotBoundException {
    var reg = LocateRegistry.getRegistry(ip);
    return (WorkerRemote) reg.lookup(WorkerRemote.NAME);
  }
  
  @Override
  public Integer call() throws Exception {
    if (!input.exists() || !input.canRead()) {
      printErr("Invalid --input parameter value. Correct file path to an existing "
          + "file with read permission is required.");
      return -1;
    }
    if (!workersFile.exists() || !workersFile.canRead()) {
      printErr("Invalid --workers parameter value. Correct file path to an existing "
          + "file with read permission is required.");
      return -1;
    }
    if (output.exists()) 
      output.delete();
    if (!output.createNewFile()) {
      printErr("Output file creation failed.");
      return -1;
    }   
    var lines = Files.readAllLines(workersFile.toPath());
    for (var ip : lines) {
      var reg = LocateRegistry.getRegistry(ip);
      var worker = (WorkerRemote) reg.lookup(WorkerRemote.NAME);
      if (!worker.getOK().equals("OK"))
        throw new RuntimeException("Host " + ip + " did not answered properly.");
      workers.add(ip);
    }
    //
    TestImpl test = new TestImpl();
    var text = test.getInputFormat();
    var splits = text.getSplits(input, workers.size());
    int i = 0;
    for (var s : splits) {
      var worker = getWorkerRemote(workers.get(i++));
      boolean exists = worker.exists(input);
      if (!exists || overwrite) {
        if (exists) worker.delete(input);
        worker.createNewFile(input);
        System.out.println("Sending split " + i + " to worker " + worker.getIp());
        var inStream = Files.newInputStream(s.toPath(), StandardOpenOption.READ);
        byte[] buf = new byte[2048];
        worker.initWrite(input);
        for (int err = inStream.read(buf); err != -1; err = inStream.read(buf)) 
          worker.write(buf);
        worker.doneWrite();
      }
    }
    for (var w : workers) {
      var worker = getWorkerRemote(w);
      worker.doMap(input, test);
    }
    System.out.println("FINISHED");
//    var reg = LocateRegistry.getRegistry(w);
//    var worker = (WorkerRemote) reg.lookup(WorkerRemote.NAME);
//    var lines = Files.readString(input.toPath());
//    int ret = worker.sendMapChunk(lines);
//    System.out.println(ret);
//    if (isMaster)
//      return new Master(input, output, workers).call();
//    else
//      return new Worker().call();
    return 0;
  }

  public static void main(String[] args) {
    int exitCode = comm.execute(args);
    System.exit(exitCode);
  }

}