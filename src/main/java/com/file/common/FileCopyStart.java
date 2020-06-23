package com.file.common;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.file.common.excel.ExcelDataObject;
import com.file.common.excel.ImportExcel;
import com.file.common.properties.Global;

import de.innosystec.unrar.exception.RarException;

public class FileCopyStart {

	private static Logger logger = LoggerFactory.getLogger(FileCopyStart.class);
	//监控目录相关信息获取（监控目录、上一交易日截止时间、拷贝的目标目录）
	public static List<ExcelDataObject> monitorDirList = new ArrayList<ExcelDataObject>();
	//交易日期获取
	private static List<ExcelDataObject> tradeDateList = new ArrayList<ExcelDataObject>();
	//压缩文件及对应密码获取
	private static List<ExcelDataObject> packFileList = new ArrayList<ExcelDataObject>();
	//拷贝文件源目录和目标目录
	private static Map<String,List<String>> copyFiles = new HashMap<String,List<String>>();
	//存儲目标目录和对应的上一交易日截止时间
	private static Map<String,String> targetFilePathTrade = new HashMap<String,String>();
	//存储压缩文件名称和密码的对应关系
	private static Map<String,String> packAndPwd = new HashMap<String,String>();
	//文件过滤包含的后缀名
	private static Map<String,String> filterFileSuffix = new HashMap<String,String>();
	//从config配置文件中获取相关excel文件路径
	private static String monitorFilePath = Global.getConfig("monitorFilePath");
	private static String packFilePath = Global.getConfig("packFilePath");
	private static String tradeDateFilePath = Global.getConfig("tradeDateFilePath");
	private static String monitorMinutes = Global.getConfig("monitorMinutes");
	//项目启动时是否立即拷备已有文件，0：是，1：否
	private static String startCopyFile = Global.getConfig("startCopyFile");
	private static SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
	public static String currDate = sdf.format(new Date());
	public static FileAlterationMonitor monitor = null;
	//更新监控目录延后时间大小，在获取目标目录时间路径时使用
	private static String delayDate = Global.getConfig("delayDate");
	//从配置文件中获取winrar的安装路径
	public static String winrarPath = Global.getConfig("winrarPath");
	//线程池spring配置,利用java程序解压失败是，启用新的线程调用windows的winrar程序进行解压
	//启用新线程的目标主要是为了防止有密码文件未获取到密码导致程序卡主不能继续往下跑
	public static ThreadPoolTaskExecutor taskExecutor = null;
	
	public static void main(String[] args) {
		try {
			//从监控的excel中获取到监控目录和目标目录
			ImportExcel eiMonitor = new ImportExcel(new File(monitorFilePath), 0, 0);
			monitorDirList = eiMonitor.getDataList(ExcelDataObject.class);
			//从交易日excel中获取交易日期
			ImportExcel eiTradeDate = new ImportExcel(new File(tradeDateFilePath), 0, 0);
			tradeDateList = eiTradeDate.getDataList(ExcelDataObject.class);
			//交易日期excel数据进行倒序，方便后面获取，倒序时，需要先对list进行升序排序
			Collections.sort(tradeDateList);
			Collections.reverse(tradeDateList);
			logger.debug("获取到交易日期excel表中共有："+tradeDateList.size()+"条记录");
			//从压缩文件excel中获取压缩文件名及对应的压缩密码
			ImportExcel eipackFile = new ImportExcel(new File(packFilePath), 0, 0);
			packFileList = eipackFile.getDataList(ExcelDataObject.class);
			logger.debug("获取到压缩文件excel表中共有："+packFileList.size()+"条记录");
			ApplicationContext ac=new ClassPathXmlApplicationContext("classpath:spring-context.xml");
			taskExecutor = (ThreadPoolTaskExecutor)ac.getBean("taskExecutor");
			addMonitorDir("0");
		} catch (Exception e) {
			logger.error("从excel中获取数据信息出错，错误信息为："+e.getMessage(),e);
		}
	}
	/**
	 * 
	 * @Description: 添加监控目录，启动监控
	 * @author songy
	 * @param type 0:表示项目启动，1:表示切换监控目录
	 * @date 2019年7月15日 下午2:26:46
	 */
	public static void addMonitorDir(String type){
		// 配置文件中获取监控扫描时间间隔，设置为分钟时间，默认为1一分钟扫描一次
		Long defaultMonitorMin = 60*1000L;
		if(StringUtils.isNotEmpty(monitorMinutes)){
			defaultMonitorMin = Integer.parseInt(monitorMinutes)*1000L;
		}
		//监控路径及交易日期对应关系映射
		if(monitorDirList != null && monitorDirList.size()>0){
			monitor = new FileAlterationMonitor(defaultMonitorMin);
			FileListerAdapter listener = new FileListerAdapter();
			logger.debug("获取到监控路径excel表中共有："+monitorDirList.size()+"条记录");
			copyFiles.clear();
			targetFilePathTrade.clear();
			filterFileSuffix.clear();
			for(ExcelDataObject monitorDir:monitorDirList){
				String sourceFilePath = monitorDir.getFieldsFirst().replaceAll("yyyyMMdd", currDate);
				String targetFilePath = monitorDir.getFieldsThree();
				logger.debug("开始遍历拷贝文件的相关路径，并将扫描路径放入监控中，当前扫描路径为："+sourceFilePath);
				if(StringUtils.isNotEmpty(targetFilePath)){
					String[] targetDir = targetFilePath.split(",");
					List<String> targetDirs = new ArrayList<String>(targetDir.length);
					Collections.addAll(targetDirs,targetDir);
					copyFiles.put(sourceFilePath, targetDirs);
					targetFilePathTrade.put(sourceFilePath, monitorDir.getFieldsSecond());
					//添加拷备文件过滤，如果为空则不添加到集合中，可以减小集合大小
					if(StringUtils.isNotEmpty(sourceFilePath) && StringUtils.isNotEmpty(monitorDir.getFiledsFour())){
						filterFileSuffix.put(sourceFilePath, monitorDir.getFiledsFour());
						//每次更新监控路径时，清空临时路径下的文件
						if(sourceFilePath.indexOf("临时")>0){
							String filePathTemp = sourceFilePath.substring(0,sourceFilePath.lastIndexOf("\\"));
							File file = new File(filePathTemp);
							if(file.isDirectory() && file.exists()){
								try {
									FileUtils.cleanDirectory(file);
								} catch (IOException e) {
									logger.debug("更新监控路径时，删除临时路径下的文件夹及文件报错，错误信息为："+e.getMessage(),e);
								}							
							}
						}
					}
				}
				if(StringUtils.isNotEmpty(sourceFilePath)){
					FileAlterationObserver observer = new FileAlterationObserver(new File(sourceFilePath));
					observer.addListener(listener);
					monitor.addObserver(observer);
				}
			}
		}
		//压缩文件名及密码关系映射
		if(packFileList != null && packFileList.size()>0){
			String packFileName = "";
			packAndPwd.clear();
			for(ExcelDataObject monitorDir:packFileList){
				packFileName = monitorDir.getFieldsFirst();
				if(StringUtils.isNotEmpty(packFileName)){
					packFileName = packFileName.replace("yyyyMMdd", currDate);
				}
				packAndPwd.put(packFileName, monitorDir.getFieldsSecond());
			}
		}
		try {
			monitor.start();
			//设置监控目录，获取当前是否将已有文件拷备到目标目录，0:项目启动，1:切换监控目录
			//从配置文件中获取项目启动后是否立即开始拷备文件，0：是，1：否
			logger.debug("切换监控路径type："+type+"，项目启动是否拷备startCopyFile："+startCopyFile);
			if((StringUtils.isNotEmpty(type) && "1".equals(type)) || (StringUtils.isNotEmpty(startCopyFile) && "0".equals(startCopyFile))){
				startFunc();
			}
		} catch (Exception e) {
			logger.error("添加监控目录出错，错误信息为："+e.getMessage(),e);
		}
	}
	/**
	 * 
	 * @Description: 根据传入的源目录路径，从map中获取对应的目标目录
	 * @author songy
	 * @date 2019年7月8日 下午1:56:31
	 */
	public static Map<String,List<String>> getTargetDir(String filePath){
		Map<String,List<String>> map = new HashMap<String,List<String>>();
		List<String> targetDirs = new ArrayList<String>();
		List<String> sourcePath = new ArrayList<String>();
		String targetDir = "";
		String fileSuffix = "";
		if(copyFiles != null && copyFiles.size()>0 && StringUtils.isNotEmpty(filePath)){
			logger.debug("执行getTargetDir方法，根据原路径获取目标路径，当前原路径为："+filePath);
			filePath = filePath.replaceAll("\\\\", "\\\\\\\\");
			Set set = copyFiles.keySet();
			for(Iterator iter = set.iterator(); iter.hasNext();){
				String key = (String)iter.next();
				if(StringUtils.isNotEmpty(key) && filePath.contains(key)){
					String pathTemp = filePath.substring(key.length(),filePath.length());
					List<String> val = copyFiles.get(key);
					if(val != null && val.size()>0){
						String dateDir = getTradeDate(key);
						fileSuffix = getFilterFileSuffix(key);
						for(String targetTemp:val){
							if(StringUtils.isNotEmpty(pathTemp)){
								if(!pathTemp.startsWith("\\")){
									//logger.debug("当前截取的中间路径为："+pathTemp+"，截取路径有问题，按照\\进行再次截取");
									//pathTemp = pathTemp.substring(pathTemp.indexOf("\\"), pathTemp.length());
									pathTemp = File.separator+pathTemp;
								}
							}
							targetDir = targetTemp + File.separator + dateDir + pathTemp;
							logger.debug("getTargetDir根据文件源目录结构，获取配置源目录以外的路径："+pathTemp+"，完整目标路径为："+targetDir);
							targetDirs.add(targetDir);
							sourcePath.add(targetTemp + File.separator + dateDir);
						}
					}
					break;
				}
			}
			map.put("targetDirs", targetDirs);
			map.put("sourcePath", sourcePath);
			if(StringUtils.isNotEmpty(fileSuffix)){
				String[] fileSuffixs = fileSuffix.split(",");
				map.put("fileSuffix",  Arrays.asList(fileSuffixs));				
			}
		}
		
		return map;
	}
	
	/**
	 * 
	 * @Description: 根据目标目录获取上一交易日截止时间，并根据当前时间判断获取最后存储目录时间路径
	 * @author songy
	 * @date 2019年7月8日 下午2:10:51
	 */
	public static String getTradeDate(String targetDir){
		String tradeDir = "";
		if(StringUtils.isNotEmpty(targetDir)){
			String preTradeDate = targetFilePathTrade.get(targetDir);
			if(StringUtils.isNotBlank(preTradeDate)){
				SimpleDateFormat sdfs = new SimpleDateFormat("HH");
				String h = sdfs.format(new Date());
				//如果当前时间小于延后时间，则获取上一天的交易日时间
				if(!currDate.equals(sdf.format(new Date())) && Integer.parseInt(h) <= Integer.parseInt(delayDate)){
					h="23";
				}
				if(tradeDateList != null && tradeDateList.size()>0){
					boolean flag = false;
					for(ExcelDataObject obj:tradeDateList){
						if(Integer.parseInt(h) < Integer.parseInt(preTradeDate)){
							if(currDate.equals(obj.getFieldsFirst())){
								logger.debug("当前时间小于上一交易日截止时间,需从excel中获取上一交易日时间");
								flag = true;
								continue;
							}
							if(flag && "1".equals(obj.getFieldsSecond())){
								tradeDir = obj.getFieldsFirst();
								logger.debug("获取到的交易时间为："+tradeDir);
								break;
							}
						}else{
							if(currDate.equals(obj.getFieldsFirst())){
								flag = true;
								if("1".equals(obj.getFieldsSecond())){
									tradeDir = obj.getFieldsFirst();
									logger.debug("当前时间大于上一交易日截止时间，并且当天即为交易日时间："+tradeDir);
									break;
								}
							}else if(flag && "1".equals(obj.getFieldsSecond())){
								tradeDir = obj.getFieldsFirst();
								logger.debug("当前时间大于上一交易日截止时间，当天不为交易日，获取到交易日时间为："+tradeDir);
								break;
							}
						}
					}
				}
			}
		}
		return tradeDir;
	}
	/**
	 * 
	 * @Description: 解压文件获取
	 * 1、先根据整体文件名进行匹配，如匹配到则直接返回，适用于当天传输解压文件使用，文件名格式如“测试中信建投基金产品20190820.rar”,excel中配置如“测试中信建投基金产品yyyyMMdd.rar”
	 * 2、根据年月进行分割文件名，之后按照分割后的前半部分进行匹配，如匹配到则返回，适用于传输前一天带有密码的压缩文件，文件格式如“测试中信建投基金产品20190820.rar”,excel配置如“测试中信建投基金产品”
	 * 3、根据年进行分割后按照分割的前半部分进行匹配，考虑到2中遇到跨年按照年月无法匹配到，适用场景及配置方式同2
	 * @author songy
	 * @date 2019年7月8日 下午3:20:25
	 */
	public static String getUnPackFilePwd(String fileName){
		String pwd = "";
		if(packAndPwd != null && StringUtils.isNotEmpty(fileName)){
			for (String key : packAndPwd.keySet()) {
	           if(key.toLowerCase().equals(fileName.toLowerCase())){
	        	   pwd = packAndPwd.get(key);
	        	   logger.debug("当前文件名为："+fileName+",按整体文件名进行匹配获取到的密码为："+pwd);
	        	   break;
	           }
	        }
			if(StringUtils.isEmpty(pwd)){
				SimpleDateFormat sdf = new SimpleDateFormat("yyyyMM");
				String date = sdf.format(new Date());
				fileName = fileName.split(date)[0];
				if(StringUtils.isNotEmpty(fileName)){
					for (String key : packAndPwd.keySet()) {
						if(key.toLowerCase().equals(fileName.toLowerCase())){
							pwd = packAndPwd.get(key);
							logger.debug("当前文件名为："+fileName+",按年月进行分割获取到的密码为："+pwd);
							break;
						}
					}
				}
			}
			if(StringUtils.isEmpty(pwd)){
				SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy");
				String date1 = sdf1.format(new Date());
				fileName = fileName.split(date1)[0];
				if(StringUtils.isNotEmpty(fileName)){
					for (String key : packAndPwd.keySet()) {
						if(key.toLowerCase().equals(fileName.toLowerCase())){
							pwd = packAndPwd.get(key);
							logger.debug("当前文件名为："+fileName+",按年进行分割获取到的密码为："+pwd);
							break;
						}
					}
				}
			}
		}
		return pwd;
	}
	/**
	 * 
	 * @Description: 项目启动后，将监控目录下原有文件直接拷备到目标目录下
	 * @author songy
	 * @date 2019年7月12日 下午2:29:21
	 */
	public static void startFunc(){
		if(monitorDirList != null && monitorDirList.size()>0){
			logger.debug("项目启动，将监控目录下的文件拷备到目标目录下，并进行相应的解压");
			for(ExcelDataObject monitorDir:monitorDirList){
				String sourceFilePath = monitorDir.getFieldsFirst().replaceAll("yyyyMMdd", currDate);
				String targetFilePath = monitorDir.getFieldsThree();
				String fileFilter = monitorDir.getFiledsFour();
				List<String> fileSuffix = new ArrayList<String>();
				if(StringUtils.isNotEmpty(fileFilter)){
					fileSuffix = Arrays.asList(fileFilter.split(","));
				}
				if(StringUtils.isNotEmpty(targetFilePath)){
					String[] targetDirs = targetFilePath.split(",");
					String targetDir = FileCopyStart.getTradeDate(sourceFilePath);
					String targetCopyFilePath ="";
					for(String targetTemp:targetDirs){
						if(StringUtils.isNotEmpty(targetTemp)){
							targetFilePath = targetTemp + File.separator+targetDir;
							targetCopyFilePath = targetFilePath + File.separator+"startCopy";
						}
						if(StringUtils.isNotEmpty(sourceFilePath) && StringUtils.isNotEmpty(targetFilePath)){
							File sourceFile = new File(sourceFilePath);
							if(sourceFile.exists()){
								logger.debug("startFunc开始拷备文件:"+sourceFilePath+"，目标："+targetFilePath);
								try {
									FileUtils.copyDirectory(sourceFile, new File(targetCopyFilePath));
								} catch (IOException e) {
									logger.error("项目启动拷备时报错，错误信息为："+e.getMessage(),e);
								}
								//20191029将监控路径下文件直接拷贝到目标路径下，解压出现无限循环问题
								//凌晨更新目录后，拷备源路径下存在文件，拷备时，先将文件拷备到目标路径的临时目录下，之后判断如果是文件夹，
								//则遍历文件夹下文件拷备到目标路径+文件夹名称的路径下， 如果是压缩文件，则解压缩到目标路径+压缩文件名路径下
								copyAndUnpark(targetFilePath,targetDir,fileSuffix,targetCopyFilePath);
							}
						}
					}
				}
			}
			
		}
	}
	/**
	 * 
	 * @Description: 项目启动、切换监控路径，将监控路径下的文件直接拷备到目标目录下
	 * @author songy
	 * @date 2019年7月11日 上午11:27:30
	 */
	public static void copyAndUnpark(String filePath,String dateDir,List<String> fileFilter,String targetCopyFilePath){
		if(StringUtils.isNotEmpty(targetCopyFilePath)){
			File file = new File(targetCopyFilePath);
			File[] files = file.listFiles();
			if(files != null && files.length>0){
				for(File fileTemp:files){
					if(fileTemp.isDirectory()){
						FileListerAdapter.cycleCopyFile(fileTemp, filePath, "0", fileFilter);
					}else{
						try {
							String targetFilePath = filePath+File.separator+fileTemp.getName().replace(" ","");
							FileUtils.copyFile(fileTemp, new File(targetFilePath));
							if(fileTemp.isFile() && StringUtils.isNotEmpty(fileTemp.getName()) && fileTemp.getName().toLowerCase().indexOf(".rar.szt") == -1
									&& fileTemp.getName().toLowerCase().indexOf(".zip.szt") == -1 && (fileTemp.getName().toLowerCase().indexOf(".rar")>-1 || fileTemp.getName().toLowerCase().indexOf(".zip")>-1)){
								FileListerAdapter.unpackFileCommons(new File(targetFilePath),targetFilePath,filePath,"cycleCopyFile");
							}
						} catch (RarException | IOException e) {
							logger.error("解压文件出错，错误信息为："+e.getMessage(),e);
						}
					}
				}
				try {
					File fileCopy = new File(targetCopyFilePath);
					FileUtils.deleteDirectory(fileCopy);
				} catch (IOException e) {
					logger.error("初始化拷备文件后将临时路径下的文件清空时出错，错误信息为："+e.getMessage(),e);
				}
			}
		}
	}
	/**
	 * 
	 * @Description: 根据监控路径获取对应过滤文件的后缀名
	 * @author songy
	 * @date 2019年8月16日 上午9:21:43
	 */
	public static String getFilterFileSuffix(String sourceFilePath){
		String fileSuffix = "";
		if(filterFileSuffix != null && filterFileSuffix.size()>0){
			for (String key : filterFileSuffix.keySet()) {
	           if(key.equals(sourceFilePath)){
	        	   fileSuffix = filterFileSuffix.get(key);
	        	   break;
	           }
	        }
		}
		logger.debug("根据文件源路径："+sourceFilePath+"，获取到文件过滤的后缀名为："+fileSuffix);
		return fileSuffix;
	}
	
	/**
	 * 
	 * @Description: 根据文件及需要过滤的文件后缀集合判断当前拷备的文件是否需要过滤掉
	 * @author songy
	 * @date 2019年8月16日 上午9:43:50
	 */
	public static boolean getfilterFile(File file,List<String> fileSuffixs){
		boolean flag = false;
		String fileSuffix = file.getName().substring(file.getName().lastIndexOf(".")+1, file.getName().length());
		if(fileSuffixs != null && fileSuffixs.size()>0 && fileSuffixs.contains(fileSuffix.toLowerCase())){
			flag = true;
		}
		return flag;
	}
}
