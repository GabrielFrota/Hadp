package params;

import java.io.File;
import java.io.IOException;

public class TestImplq implements MapReduce<String, String, String, String> {

  @Override
  public InputFormat<String, String> getInputFormat() {
    return new TextInputFormat();
  }

  @Override
  public RecordWriter<String, String> getMapWriter(File out) throws IOException {
    var rec = new TextRecordWriter();
    rec.init(out);
    return rec;
  }

  @Override
  public void map(String k, String v, RecordWriter<String, String> w) throws IOException {
    w.write(k, "111");
  }
  
}

