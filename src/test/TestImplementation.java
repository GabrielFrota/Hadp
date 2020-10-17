package test;

import java.io.IOException;

import params.InputFormat;
import params.MapReduce;
import params.RecordWriter;
import params.TextInputFormat;

public class TestImplementation implements MapReduce<String, String, String, String> {

  private static final long serialVersionUID = 1L;

  @Override
  public InputFormat<String, String> getInputFormat() {
    return new TextInputFormat();
  }

  @Override
  public void map(String k, String v, RecordWriter<String, String> w) throws IOException {
    w.write(k, "11");
  }
  
}


