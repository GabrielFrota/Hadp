package impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import interf.InputFormat;
import interf.RecordReader;

public class TextInputFormat implements InputFormat<Long, String> {
  
  @Override
  public File[] getSplits(File in, int numSplits) throws IOException {
    long len = in.length();
    long splitLen = len / numSplits;
    var splits = new File[numSplits];
    var reader = new BufferedReader(new FileReader(in));
    for (int i = 0; i < numSplits; i++) {
      var file = new File(in.getName() + "." + i);
      var out = new PrintWriter(new FileWriter(file));
      long cnt = 0;
      while (cnt <= splitLen) {
        var line = reader.readLine();
        if (line == null) break;
        out.println(line);
        cnt += line.getBytes().length;
      }
      splits[i] = file;
      out.close();
    }
    reader.close();
    return splits;
  }
  
  @Override
  public RecordReader<Long, String> getRecordReader(File in) throws IOException {
    return new LineRecordReader(in);
  }
  
}
