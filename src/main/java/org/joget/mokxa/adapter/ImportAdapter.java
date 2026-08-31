package org.joget.mokxa.adapter;

import java.io.File;
import java.util.List;

import org.joget.mokxa.model.ClientImportRow;
import org.joget.mokxa.model.ImportOptions;

public interface ImportAdapter {

    String getSource();

    List<ClientImportRow> read(File file, ImportOptions options) throws Exception;

    List<String> getOriginalHeaders();
}