package org.daria.logic;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.inject.Inject;
import javax.inject.Named;
import javax.sql.DataSource;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.daria.DariaException;
import org.daria.DbType;
import org.daria.data.Column;
import org.daria.data.TableInfo;
import org.daria.data.ValueType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yipuran.mybatis.util.SQLProcess;
import org.yipuran.util.GenericBuilder;

import com.google.common.reflect.TypeToken;
import com.google.gson.GsonBuilder;

/**
 * DariaLogicImpl
 */
public class DariaLogicImpl implements DariaLogic{
	@Inject @Named("SCHEME") String scheme;
	@Inject @Named("EXCEL")  String excelPath;
	@Inject DbType dbtype;
	@Inject DataSource source;
	private String setteingJson = "{ \"mapUnderscoreToCamelCase\":true  }";

	Logger logger = LoggerFactory.getLogger(this.getClass());

	/* Excel チェック
	 * @see org.daria.logic.DariaLogic#parseExcel()
	 */
	@Override
	public void parseExcel() throws DariaException{
		Map<String, Object> settingmap = new GsonBuilder().create().fromJson(setteingJson, new TypeToken<Map<String, Object>>(){}.getType());
		SQLProcess process = GenericBuilder.of(()->new SQLProcess(settingmap)).with(SQLProcess::setDatasource, source).build();
		List<TableInfo> tablelist = process.apply(InfoMapper.class, s->s.selectList(InfoMapper.class.getName() + ".getTables" + dbtype.name(), scheme));
		try(InputStream is = new FileInputStream(excelPath); XSSFWorkbook book = new XSSFWorkbook(is)){
			List<String> sheetNames = IntStream.range(0, book.getNumberOfSheets()).boxed().map(i->book.getSheetAt(i).getSheetName()).collect(Collectors.toList());
			sheetNames.stream().forEach(sheetname->{
				if (!tablelist.stream().anyMatch(t->sheetname.equals(t.tableName))) {
					throw new DariaException("テーブル：" + sheetname + " は、存在しません。");
				}
				Map<String, String> map = new HashMap<String, String>();
				map.put("scheme", scheme);
				map.put("tablename", sheetname);
				List<Column> list = 	process.apply(InfoMapper.class, s->s.selectList(InfoMapper.class.getName() + ".getColumn" + dbtype.name(), map));

				Map<String, Boolean> requireMap = new HashMap<>();
				Map<String, Column> columnMap = list.stream().collect(()->new HashMap<String, Column>(), (r, t)->{
					String d = t.dataType.toLowerCase();
					if (d.indexOf("char") > 0) {
						t.vtype = ValueType.STRING;
					}else if(d.equals("date")){
						t.vtype = ValueType.DATE;
					}else if(d.equals("datetime")){
						t.vtype = ValueType.DATETIME;
					}else if(d.equals("timestamp")){
						t.vtype = ValueType.DATETIME;
					}else{
						t.vtype = ValueType.NUMERIC;
					}
					requireMap.put(t.columnName.toUpperCase(), !t.isNullable);
					r.put(t.columnName.toUpperCase(), t);
				}, (r, t)->{});

				XSSFSheet sheet = book.getSheet(sheetname);
				int lastRowNum = sheet.getLastRowNum();
				XSSFRow headrow = sheet.getRow(0);
				int lastCellNum = (int)headrow.getLastCellNum();
				List<String> columnList = new ArrayList<>();
				IntStream.range(0, lastCellNum).boxed().forEach(i->{
					String key = headrow.getCell(i).getStringCellValue().toUpperCase();
					if (!columnMap.containsKey(key)) throw new RuntimeException("Excel Error : 列名 " + key + " はテーブルで未定義です" );
					if (requireMap.containsKey(key)) requireMap.put(key, false);
					columnList.add(key);
				});
				List<String> requireErrlist = requireMap.entrySet().stream().filter(e->e.getValue()).map(e->e.getKey()).collect(Collectors.toList());
				if (requireErrlist.size() > 0){
					throw new RuntimeException("Excel Error : 列が不足してます " + requireErrlist.stream().collect(Collectors.joining(", ")) );
				}
				IntStream.rangeClosed(1, lastRowNum).boxed().forEach(n->{
					XSSFRow row = sheet.getRow(n);
					List<Object> valuelist = new ArrayList<>();
					for(int i=0;i < lastCellNum; i++){
						XSSFCell cel = row.getCell(i);
						CellType type = cel.getCellType();
						ValueType vtype = columnMap.get(columnList.get(i)).vtype;
						if (type.equals(CellType.NUMERIC)){
							if (DateUtil.isCellDateFormatted(cel)){
								if (vtype.equals(ValueType.DATE)){
									valuelist.add(cel.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
								}else if(vtype.equals(ValueType.DATETIME)){
									valuelist.add(cel.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
								}else{
									throw new RuntimeException("データベース　列の型 " + vtype + " に一致しません " + columnList.get(i) + "列  " + n + "行目");
								}
							}else{
								valuelist.add((int)cel.getNumericCellValue());
							}
						}else if(type.equals(CellType.STRING)){
							valuelist.add(cel.getStringCellValue());
						}else if(type.equals(CellType.BLANK)){
							valuelist.add(null);
							if (!columnMap.get(columnList.get(i)).isNullable) {
								throw new RuntimeException("Excel 必須エラー " + columnList.get(i) + "列  " + n + "行目");
							}
						}else{
							throw new RuntimeException("Excel 型認識エラー " + columnList.get(i) + "列  " + n + "行目");
						}
					}
				});
			});
		}catch(Exception ex){
			throw new DariaException(ex.getMessage(), ex);
		}
	}

	/* TRUNCATE → INSERT → commit
	 * @see org.daria.logic.DariaLogic#execute()
	 */
	@Override
	public void execute() throws DariaException{
		Map<String, Object> settingmap = new GsonBuilder().create().fromJson(setteingJson, new TypeToken<Map<String, Object>>(){}.getType());
		SQLProcess process = GenericBuilder.of(()->new SQLProcess(settingmap)).with(SQLProcess::setDatasource, source).build();
		try(InputStream is = new FileInputStream(excelPath); XSSFWorkbook book = new XSSFWorkbook(is)){
			List<String> sheetNames = IntStream.range(0, book.getNumberOfSheets()).boxed().map(i->book.getSheetAt(i).getSheetName()).collect(Collectors.toList());
			sheetNames.stream().forEach(sheetname->{
				Map<String, String> map = new HashMap<String, String>();
				map.put("scheme", scheme);
				map.put("tablename", sheetname);
				List<Column> list = 	process.apply(InfoMapper.class, s->s.selectList(InfoMapper.class.getName() + ".getColumn" + dbtype.name(), map));

				Map<String, Column> columnMap = list.stream().collect(()->new HashMap<String, Column>(), (r, t)->{
					String d = t.dataType.toLowerCase();
					if (d.indexOf("char") > 0) {
						t.vtype = ValueType.STRING;
					}else if(d.equals("date")){
						t.vtype = ValueType.DATE;
					}else if(d.equals("datetime")){
						t.vtype = ValueType.DATETIME;
					}else if(d.equals("timestamp")){
						t.vtype = ValueType.DATETIME;
					}else{
						t.vtype = ValueType.NUMERIC;
					}
					r.put(t.columnName.toUpperCase(), t);
				}, (r, t)->{});

				XSSFSheet sheet = book.getSheet(sheetname);
				int lastRowNum = sheet.getLastRowNum();
				XSSFRow headrow = sheet.getRow(0);
				int lastCellNum = (int)headrow.getLastCellNum();
				List<String> columnList = new ArrayList<>();
				IntStream.range(0, lastCellNum).boxed().forEach(i->{
					columnList.add(headrow.getCell(i).getStringCellValue().toUpperCase());
				});

				process.accept(InfoMapper.class, s->s.update(InfoMapper.class.getName() + ".truncateFor" + dbtype, sheetname));
				logger.info("■ TRUNCATE TABLE "+ sheetname);

				IntStream.rangeClosed(1, lastRowNum).boxed().forEach(n->{
					XSSFRow row = sheet.getRow(n);
					List<Object> valuelist = new ArrayList<>();
					for(int i=0;i < lastCellNum; i++){
						XSSFCell cel = row.getCell(i);
						CellType type = cel.getCellType();
						ValueType vtype = columnMap.get(columnList.get(i)).vtype;
						if (type.equals(CellType.NUMERIC)){
							if (DateUtil.isCellDateFormatted(cel)){
								if (vtype.equals(ValueType.DATE)){
									valuelist.add(cel.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
								}else if(vtype.equals(ValueType.DATETIME)){
									valuelist.add(cel.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
								}
							}else{
								valuelist.add((int)cel.getNumericCellValue());
							}
						}else if(type.equals(CellType.STRING)){
							valuelist.add(cel.getStringCellValue());
						}else if(type.equals(CellType.BLANK)){
							valuelist.add(null);
						}
					}
					Map<String, Object> pmap = new HashMap<>();
					pmap.put("tablename", sheetname);
					pmap.put("columns", columnList);
					pmap.put("values", valuelist);
					process.acceptUpdate(DariaMapper.class, s->{
						s.insert(DariaMapper.class.getName() + ".insert", pmap);
					});
				});
				logger.info("■ TABLE " + sheetname + "  insert " + (lastRowNum - 1)+ " rows");
			});
		}catch(Exception ex){
			throw new DariaException(ex.getMessage(), ex);
		}
	}

	/* SQL生成
	 * @see org.daria.logic.DariaLogic#outSql(java.lang.String)
	 */
	@Override
	public void outSql(String outPath) throws DariaException{
		Map<String, Object> settingmap = new GsonBuilder().create().fromJson(setteingJson, new TypeToken<Map<String, Object>>(){}.getType());
		SQLProcess process = GenericBuilder.of(()->new SQLProcess(settingmap)).with(SQLProcess::setDatasource, source).build();
		try(InputStream is = new FileInputStream(excelPath); XSSFWorkbook book = new XSSFWorkbook(is);
				PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File(outPath)), StandardCharsets.UTF_8), true)){
			List<String> sheetNames = IntStream.range(0, book.getNumberOfSheets()).boxed().map(i->book.getSheetAt(i).getSheetName()).collect(Collectors.toList());
			sheetNames.stream().forEach(sheetname->{
				Map<String, String> map = new HashMap<String, String>();
				map.put("scheme", scheme);
				map.put("tablename", sheetname);
				List<Column> list = 	process.apply(InfoMapper.class, s->s.selectList(InfoMapper.class.getName() + ".getColumn" + dbtype.name(), map));

				Map<String, Column> columnMap = list.stream().collect(()->new HashMap<String, Column>(), (r, t)->{
					String d = t.dataType.toLowerCase();
					if (d.indexOf("char") > 0) {
						t.vtype = ValueType.STRING;
					}else if(d.equals("date")){
						t.vtype = ValueType.DATE;
					}else if(d.equals("datetime")){
						t.vtype = ValueType.DATETIME;
					}else if(d.equals("timestamp")){
						t.vtype = ValueType.DATETIME;
					}else{
						t.vtype = ValueType.NUMERIC;
					}
					r.put(t.columnName.toUpperCase(), t);
				}, (r, t)->{});

				XSSFSheet sheet = book.getSheet(sheetname);
				int lastRowNum = sheet.getLastRowNum();
				XSSFRow headrow = sheet.getRow(0);
				int lastCellNum = (int)headrow.getLastCellNum();
				List<String> columnList = new ArrayList<>();
				IntStream.range(0, lastCellNum).boxed().forEach(i->{
					columnList.add(headrow.getCell(i).getStringCellValue().toUpperCase());
				});

				IntStream.rangeClosed(1, lastRowNum).boxed().forEach(n->{
					XSSFRow row = sheet.getRow(n);
					List<String> valuelist = new ArrayList<>();
					for(int i=0;i < lastCellNum; i++){
						XSSFCell cel = row.getCell(i);
						CellType type = cel.getCellType();
						ValueType vtype = columnMap.get(columnList.get(i)).vtype;
						if (type.equals(CellType.NUMERIC)){
							if (DateUtil.isCellDateFormatted(cel)){
								if (vtype.equals(ValueType.DATE)){
									String datevalue	= dateSQLvalue(cel.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
									valuelist.add(datevalue);
								}else if(vtype.equals(ValueType.DATETIME)){
									String timevalue = datetimeSQLvalue(cel.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
									valuelist.add(timevalue);
								}
							}else{
								Integer ivalue = (int)cel.getNumericCellValue();
								valuelist.add(ivalue.toString());
							}
						}else if(type.equals(CellType.STRING)){
							valuelist.add("'"+cel.getStringCellValue()+"'");
						}else if(type.equals(CellType.BLANK)){
							valuelist.add("null");
						}
					}
					Map<String, Object> pmap = new HashMap<>();
					pmap.put("tablename", sheetname);
					pmap.put("columns", columnList);
					pmap.put("values", valuelist);
					// SQL文出力
					pw.print("INSERT INTO ");
					pw.print(sheetname);
					pw.print(" (");
					pw.print(columnList.stream().collect(Collectors.joining(", ")));
					pw.print(") VALUES (");
					pw.print(valuelist.stream().collect(Collectors.joining(", ")));
					pw.print(");\n");
				});
				pw.print("\n");
			});
		}catch(Exception ex){
			throw new DariaException(ex.getMessage(), ex);
		}
	}
	private String dateSQLvalue(LocalDate d) {
		if (dbtype.equals(DbType.Oracle)) {
			return "TO_DATE('" + d.toString() + "', 'YYYY-MM-DD')";
		}
		if (dbtype.equals(DbType.MySQL)) {
			return "STR_TO_DATE('" + d.toString() + "', '%Y-%m-%d')";
		}
		if (dbtype.equals(DbType.PostgreSQL)) {
			return "TO_DATE('" + d.toString() + "', 'YYYY-MM-DD')";
		}
		if (dbtype.equals(DbType.SQLServer)) {
			return "CONVERT(DATETIME, '" + d.format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "')";
		}
		if (dbtype.equals(DbType.H2)) {
			return "TO_DATE('" + d.toString() + "', 'YYYY-MM-DD')";
		}
		return null;
	}
	private String datetimeSQLvalue(LocalDateTime t) {
		if (dbtype.equals(DbType.Oracle)) {
			return "TO_DATE('" + t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "', 'YYYY-MM-DD %H24:MI:SS')";
		}
		if (dbtype.equals(DbType.MySQL)) {
			return "STR_TO_DATE('" + t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "', '%Y-%m-%d %H:%i:%s')";
		}
		if (dbtype.equals(DbType.PostgreSQL)) {
			return "TO_TIMESTAMP('" + t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "', 'YYYY-MM-DD %H24:MI:SS')";
		}
		if (dbtype.equals(DbType.SQLServer)) {
			return "CONVERT(DATETIME, '" + t.format(DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss")) + "')";
		}
		if (dbtype.equals(DbType.H2)) {
			return "TO_DATE('" + t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "', 'YYYY-MM-DD %H24:MI:SS')";
		}
		return null;
	}
}
