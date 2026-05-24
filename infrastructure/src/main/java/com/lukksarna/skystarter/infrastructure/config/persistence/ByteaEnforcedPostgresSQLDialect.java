package com.lukksarna.skystarter.infrastructure.config.persistence;

import org.hibernate.boot.model.TypeContributions;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.jdbc.BinaryJdbcType;
import org.hibernate.type.descriptor.jdbc.spi.JdbcTypeRegistry;

import java.sql.Types;

public class ByteaEnforcedPostgresSQLDialect extends PostgreSQLDialect {

    // Hibernate instantiates an explicitly-configured dialect through the
    // DialectResolutionInfo constructor when a JDBC connection is available, so
    // the real server version is detected at boot and newer PG SQL features
    // stay enabled. The no-arg fallback is only used in metadata-less contexts.
    public ByteaEnforcedPostgresSQLDialect(DialectResolutionInfo info) {
        super(info);
    }

    public ByteaEnforcedPostgresSQLDialect() {
        super();
    }

    @Override
    protected String columnType(int sqlTypeCode) {
        return sqlTypeCode == SqlTypes.BLOB ? "bytea" : super.columnType(sqlTypeCode);
    }

    @Override
    protected String castType(int sqlTypeCode) {
        return sqlTypeCode == SqlTypes.BLOB ? "bytea" : super.castType(sqlTypeCode);
    }

    @Override
    public void contributeTypes(TypeContributions typeContributions,
                                ServiceRegistry serviceRegistry) {
        super.contributeTypes(typeContributions, serviceRegistry);
        JdbcTypeRegistry jdbcTypeRegistry = typeContributions.getTypeConfiguration()
                                                             .getJdbcTypeRegistry();
        jdbcTypeRegistry.addDescriptor(Types.BLOB, BinaryJdbcType.INSTANCE);
    }
}