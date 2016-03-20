package io.agi.framework.sql;

import io.agi.framework.Persistence;
import io.agi.framework.PropertyAccessor;
import io.agi.framework.serialization.ModelData;
import io.agi.framework.serialization.ModelEntity;
import io.agi.framework.serialization.ModelNode;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Created by dave on 16/02/16.
 */
public class JdbcPersistence implements Persistence, PropertyAccessor.PropertyStringAccess {

//    Entity --< Properties
//           --< Data
//    Entity --- name, type, children, node
//               Could be JSON string for entities: { name: xxx, type: yyy } etc

    public static final String DRIVER_MYSQL = "com.mysql.jdbc.Driver";
    public static final String DRIVER_POSTGRESQL = "org.postgresql.Driver";

    protected String _user;
    protected String _password;
    protected String _url; // e.g. jdbc:postgresql://localhost:5432/agidb"; // https://jdbc.postgresql.org/documentation/80/connect.html

    PropertyAccessor _propertyAccessor = null;

    public JdbcPersistence() {
    }

    public void setup( String driver, String user, String password, String url ) throws ClassNotFoundException {
        Class.forName(driver);
        _user = user;
        _password = password;
        _url = url;

        _propertyAccessor = new PropertyAccessor( this );
    }

    // Nodes
    public Collection<ModelNode> getNodes() {
        String sql = "SELECT key, host, port FROM nodes";
        ResultSetMap rsm = new ResultSetMap();
        rsm._fields.add( "key" );
        rsm._fields.add( "host" );
        rsm._fields.add("port");
        executeQuery(sql, rsm);
        ArrayList<ModelNode> nodes = new ArrayList<ModelNode>();
        for( int i = 0; i < rsm._rows.size(); ++i ) {
            String key = rsm.getRowValue( i, "key" );
            String host = rsm.getRowValue(i, "host");
            String port = rsm.getRowValue(i, "port");
            ModelNode jn = new ModelNode(key, host, Integer.valueOf(port));
            nodes.add( jn );
        }
        return nodes;
    }
    public void setNode( ModelNode e ) {
        // https://www.sitepoint.com/community/t/how-to-use-on-duplicate-key-update-in-postgresql-with-php/200335/4
        String sql1 = "UPDATE nodes SET host = '" + e._host + "', port = '" + e._port + "' WHERE key = '" + e._key + "'";
        execute(sql1 );
        String sql2 = "INSERT INTO nodes (key, host,port) SELECT '"+e._key+"', '"+e._host+"', '"+e._port+"' WHERE NOT EXISTS (SELECT key from nodes WHERE key = '"+e._key+"')";
        execute( sql2 );
    }
    public ModelNode getNode( String nodeName ) {
        String sql = "SELECT key, host, port FROM nodes where key = '" + nodeName + "'";
        ResultSetMap rsm = new ResultSetMap();
        rsm._fields.add( "host" );
        rsm._fields.add( "port" );
        executeQuery(sql, rsm);
        if( rsm._rows.isEmpty() ) {
            return null;
        }
        String host = rsm.getRowValue( 0, "host" );
        String port = rsm.getRowValue( 0, "port" );
        ModelNode jn = new ModelNode( nodeName, host, Integer.valueOf( port ) );
        return jn;
    }
    public void removeNode(String nodeName) {
        String sql = "DELETE FROM nodes WHERE key = '" + nodeName +"'";
        execute(sql );
    }

    // Entities
    public Collection<ModelEntity> getEntities() {
        String sql = "SELECT key, host, port FROM nodes";
        ResultSetMap rsm = new ResultSetMap();
        rsm._fields.add( "key" );
        rsm._fields.add( "type" );
        rsm._fields.add("node");
        rsm._fields.add("parent");
        executeQuery(sql, rsm);
        ArrayList<ModelEntity> nodes = new ArrayList<ModelEntity>();
        for( int i = 0; i < rsm._rows.size(); ++i ) {
            String key = rsm.getRowValue( i, "key" );
            String type = rsm.getRowValue(i, "type");
            String node = rsm.getRowValue(i, "node");
            String parent = rsm.getRowValue(i, "parent");
            ModelEntity je = new ModelEntity(key, type, node, parent );
            nodes.add( je );
        }
        return nodes;
    }

    public Collection< String > getChildEntities( String parent ) {
        // SELECT
        String sql = "SELECT key, parent FROM entities where parent = '" + parent+ "'";
        ResultSetMap rsm = new ResultSetMap();
        rsm._fields.add( "key" );
        rsm._fields.add( "parent" );
        executeQuery(sql, rsm);

        ArrayList< String > children = new ArrayList< String >();

        for( int i = 0; i < rsm._rows.size(); ++i ) {
            String key = rsm.getRowValue(i, "key");
            children.add( key );
        }

        return children;
    }

    public void setEntity( ModelEntity e ) {
        // https://www.sitepoint.com/community/t/how-to-use-on-duplicate-key-update-in-postgresql-with-php/200335/4
        String sql1 = "UPDATE entities SET type = '" + e._type + "', node = '" + e._node + "', parent = '" + e._parent + "' WHERE key = '" + e._key + "'";
        execute(sql1 );
        String sql2 = "INSERT INTO entities (key, type, node,parent) SELECT '"+e._key+"', '"+e._type+"', '"+e._node+"', '"+e._parent+"' WHERE NOT EXISTS (SELECT key from entities WHERE key = '"+e._key+"')";
        execute( sql2 );
    }
    public ModelEntity getEntity( String key ) {
        String sql = "SELECT type, node, parent FROM entities where key = '" + key+"'";
        ResultSetMap rsm = new ResultSetMap();
        rsm._fields.add( "type" );
        rsm._fields.add( "node" );
        rsm._fields.add( "parent" );
        executeQuery(sql, rsm);
        if( rsm._rows.isEmpty() ) {
            return null;
        }
        String type = rsm.getRowValue( 0, "type" );
        String node = rsm.getRowValue( 0, "node" );
        String parent = rsm.getRowValue( 0, "parent" );
        ModelEntity je = new ModelEntity( key, type, node, parent );
        return je;
    }
    public void removeEntity(String key) {
        String sql = "DELETE FROM entities WHERE key = '" + key +"'";
        execute(sql );
    }

    // Data
//    public Collection< String > getDataKeys() {
//    }
    public void setData( ModelData jd ) {
        String sql1 = "UPDATE data SET ref_key = '" + jd._refKey + "', sizes = '" + jd._sizes + "', elements = '" + jd._elements + "' WHERE key = '" + jd._key +"'";
        execute( sql1 );
        String sql2 = "INSERT INTO data (key, ref_key, sizes, elements) SELECT '"+jd._key+"', null, '"+jd._sizes+"', '"+jd._elements+"' WHERE NOT EXISTS (SELECT key from data WHERE key = '"+jd._key+"' )";
        execute( sql2 );
    }
    public ModelData getData( String key ) {
        String sql = "SELECT ref_key, sizes, elements FROM data where key = '" + key + "'";
        ResultSetMap rsm = new ResultSetMap();
        rsm._fields.add( "ref_key" );
        rsm._fields.add( "sizes" );
        rsm._fields.add( "elements" );
        executeQuery(sql, rsm);
        String refKey = null;
        if( !rsm._rows.isEmpty() ) {
            refKey = rsm.getRowValue( 0, "ref_key" );
            if( refKey != null ) {
                if( refKey.equals( "null" ) ) {
                    refKey = null;
                }
            }
        }
        String sizes = rsm.getRowValue( 0, "sizes" );
        String elements = rsm.getRowValue( 0, "elements" );
        ModelData jd = new ModelData( key, refKey, sizes, elements );
        return jd;
    }
    public void removeData(String key) {
        String sql = "DELETE FROM data WHERE key = '" + key +"'";
        execute(sql );
    }

    @Override
    public Float getPropertyFloat( String key, Float defaultValue ) {
        return _propertyAccessor.getPropertyFloat( key, defaultValue );
    }

    @Override
    public void setPropertyFloat( String key, float value ) {
        _propertyAccessor.setPropertyFloat( key, value );
    }

    @Override
    public Double getPropertyDouble( String key, Double defaultValue ) {
        return _propertyAccessor.getPropertyDouble( key, defaultValue );
    }

    @Override
    public void setPropertyDouble( String key, double value ) {
        _propertyAccessor.setPropertyDouble( key, value );
    }

    @Override
    public Long getPropertyLong( String key, Long defaultValue ) {
        return _propertyAccessor.getPropertyLong( key, defaultValue );
    }

    @Override
    public void setPropertyLong( String key, long value ) {
        _propertyAccessor.setPropertyLong( key, value );
    }

    @Override
    public Integer getPropertyInt( String key, Integer defaultValue ) {
        return _propertyAccessor.getPropertyInt( key, defaultValue );
    }

    @Override
    public void setPropertyInt( String key, int value ) {
        _propertyAccessor.setPropertyInt( key, value );
    }

    @Override
    public Boolean getPropertyBoolean( String key, Boolean defaultValue ) {
        return _propertyAccessor.getPropertyBoolean( key, defaultValue );
    }

    @Override
    public void setPropertyBoolean( String key, boolean value ) {
        _propertyAccessor.setPropertyBoolean( key, value );
    }

    public String getPropertyString(String key, String defaultValue ) {
        String sql = "SELECT key, value FROM properties where key = '" + key +"'";
        ResultSetMap rsm = new ResultSetMap();
        rsm._fields.add("value");
        executeQuery(sql, rsm );
        if( rsm._rows.isEmpty() ) {
            return defaultValue;
        }
        return rsm.getRowValue( 0, "value" );
    }

    public void setPropertyString(String key, String value) {
        // https://www.sitepoint.com/community/t/how-to-use-on-duplicate-key-update-in-postgresql-with-php/200335/4
        String sql1 = "UPDATE properties SET value = " + value + " WHERE key = '" + key + "'";
        execute(sql1 );
        String sql2 = "INSERT INTO properties (key, value) SELECT '"+key+"', '"+value+"' WHERE NOT EXISTS (SELECT key from properties WHERE key = '"+key+"')";
        execute( sql2 );
    }

    public void execute( String sql ) {
        JdbcUtil.Execute(_url, _user, _password, sql );
    }

    public void executeQuery( String sql, ResultSetCallback cb ) {
        JdbcUtil.ExecuteQuery(_url, _user, _password, sql, cb);
    }


}
