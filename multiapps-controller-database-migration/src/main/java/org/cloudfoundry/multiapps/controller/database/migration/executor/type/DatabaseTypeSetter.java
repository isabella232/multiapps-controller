package org.cloudfoundry.multiapps.controller.database.migration.executor.type;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public interface DatabaseTypeSetter {

    List<String> getSupportedTypes();

    void setType(int columnIndex, PreparedStatement insertStatement, Object value) throws SQLException;
}
