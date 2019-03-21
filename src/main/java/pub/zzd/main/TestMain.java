package pub.zzd.main;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.ArrayHandler;
import org.apache.commons.dbutils.handlers.ArrayListHandler;
import pub.zzd.utils.MyC3P0Utils;

import java.sql.SQLException;
import java.util.List;

/**
 * @Description :
 * @Author : ZZD
 * @CreateTime : 2019/3/21 15:25
 */
public class TestMain {
    public static void main(String[] args) throws SQLException {
        QueryRunner qr = new QueryRunner(MyC3P0Utils.getDataSource());
        String sql = "select host,port from t_proxy order by rand() limit 1";
        Object[] query = qr.query(sql, new ArrayHandler());
        System.out.println(query[0]+":"+query[1]);
    }
}
