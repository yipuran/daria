package org.daria;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.daria.logic.DariaLogic;
import org.daria.logic.DariaLogicImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yipuran.regex.RegExpress;
import org.yipuran.util.Fieldsetter;
import org.yipuran.util.GenericBuilder;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.name.Names;

/**
 * DariaMain
 */
public class DariaMain{
	/**
	 * @param args String[]
	 * @throws UnsupportedEncodingException
	 */
	public static void main(String[] args) throws UnsupportedEncodingException{
		// 起動パラメータチェック
		parseArgument(args);

		// 接続情報読込み → dbconmap
		String username = System.getProperty("user.name");
		Map<String, String> dbconmap = readDbcon(username);
		parsDbconmap(username, dbconmap);
		String scheme = dbconmap.remove("scheme");

		// 接続情報
		final DataSource source = dbconmap.entrySet().stream().collect(()->GenericBuilder.of(UnpooledDataSource::new), (r, t)->{
			r.with(Fieldsetter.of((p, u)->t.getKey()), t.getValue());
		}, (r, t)->{}).build();

		// 処理ロジック生成
		Injector injector = Guice.createInjector(new AbstractModule(){
			@Override
			protected void configure(){
				binder().bind(String.class).annotatedWith(Names.named("SCHEME")).toInstance(scheme);
				binder().bind(String.class).annotatedWith(Names.named("EXCEL")).toInstance(args[1]);
				binder().bind(DbType.class).toInstance(getDBtype(dbconmap));
				binder().bind(String.class).annotatedWith(Names.named("DRIVER")).toInstance(dbconmap.get("driver"));
				binder().bind(DataSource.class).toInstance(source);
			}
		});
		DariaLogic logic = injector.getInstance(DariaLogicImpl.class);

		// 処理実行
		Logger logger = LoggerFactory.getLogger(DariaMain.class);
		logger.info("■ Daria Logic  START");
		try{
			logic.parseExcel(args[0]);
			if (args[0].equals("-b")){
				logic.execute();
			}
			if (args[0].equals("-o")){
				logic.outSql(args[2]);
			}
		}catch(DariaException ex){
			System.err.println("#################");
			System.err.println("##### Error #####");
			System.err.println("#################");
			System.err.println(ex.getMessage());
		}catch(Exception ex){
			logger.error(ex.getMessage(), ex);
		}
		logger.info("■ Daria Logic  END");
		if (args[0].equals("-o")) {
			System.out.println("\nINSERT SQL ファイル生成 : " + args[2]);
		}
	}
	private static void parseArgument(String[] args){
		if (args.length < 2){
			errorArgment();
		}
		if (!args[0].equals("-b") && !args[0].equals("-o")){
			errorArgment();
		}
		if (args[0].equals("-b")){
			if (args.length != 2){
				errorArgment();
			}
		}else{
			if (args.length != 3){
				errorArgment();
			}
		}
		File excelfile = new File(args[1]);
		if (!excelfile.exists()){
			System.err.println("Excel ファイルが見つかりません："+args[1]);
			errorArgment();
		}
	}

	private static void errorArgment() {
		String message = getResourceText("usage.txt");
		System.err.println("起動エラー : ");
		System.err.println(message);
		System.exit(1);
	}
	private static Map<String, String> readDbcon(String username){
		Map<String, String> map = null;
		String dbconPath = "c:/Users/" + username + "/.daria/dbcon.json";
		try(FileReader fr = new FileReader(new File(dbconPath))){
			Gson gson = new GsonBuilder().serializeNulls().create();
			map = gson.fromJson(fr, new TypeToken<Map<String, String>>(){}.getType());
		}catch(Exception ex){
			ex.printStackTrace();
			System.err.println("接続情報 読込みエラー : " + dbconPath);
			System.exit(1);
		}
		return map;
	}
	/*
	 * 接続情報記載必須チェック
	 */
	private static void parsDbconmap(String username, Map<String, String> map) {
		Arrays.asList("driver", "url", "username", "password", "scheme").stream()
		.filter(e->map.get(e)==null).findFirst().ifPresent(e->{
			String message = getResourceText("errordbcon.txt");
			List<String> mlist = Arrays.asList(username, e);
			System.err.println(RegExpress.replace("\\{[0-9]+\\}", message, (t, i)->mlist.get(i)));
			System.exit(1);
		});
	}
	/*
	 * resources下のUTF-8テキストfile読込み
	 */
	private static String getResourceText(String filename){
		try(InputStream in = ClassLoader.getSystemClassLoader().getResourceAsStream(filename);
			ByteArrayOutputStream bo = new ByteArrayOutputStream()	){
			byte[] b = new byte[1024];
			int len;
			while((len=in.read(b, 0, b.length)) >= 0){
				bo.write(b, 0, len);
			}
			bo.flush();
			bo.toByteArray();
			return new String(bo.toByteArray(), StandardCharsets.UTF_8);
		}catch(IOException ex){
			ex.printStackTrace();
			throw new RuntimeException(ex);
		}finally{
		}
	}
	private static DbType getDBtype(Map<String, String> map) {
		String driver = map.get("driver").toLowerCase();
		if (driver.indexOf("oracle") > 0) return DbType.Oracle;
		if (driver.indexOf("mysql") > 0) return DbType.MySQL;
		if (driver.indexOf("postgresql") > 0) return DbType.PostgreSQL;
		if (driver.indexOf("sqlserver") > 0) return DbType.SQLServer;
		if (driver.indexOf("h2") > 0) return DbType.H2;
		return null;
	}

}
