package impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import interf.RecordReader;

public class LineRecordReader implements RecordReader<Long, String> {
  
  protected BufferedReader reader;
  protected Long currentKey;
  protected String currentValue;
  protected long cnt;
  
  public LineRecordReader(File in) throws FileNotFoundException {
    reader = new BufferedReader(new FileReader(in));
  }

  @Override
  public Long getCurrentKey() throws IOException {
    return currentKey;
  }

  @Override
  public String getCurrentValue() throws IOException {
    return currentValue;
  }

  @Override
  public boolean readOneAndAdvance() throws IOException {
    currentKey = cnt;
    currentValue = reader.readLine();
    if (currentValue == null) 
      return false;
    cnt += currentValue.getBytes().length + 1;
    return true;
  }

  @Override
  public void close() throws IOException {
    reader.close();
  }
  
}
