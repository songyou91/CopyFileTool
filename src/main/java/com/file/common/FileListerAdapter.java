package com.file.common;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.file.common.excel.ExcelDataObject;
import com.file.common.unpack.UnpackFile;

import de.innosystec.unrar.exception.RarException;

public class FileListerAdapter extends FileAlterationListenerAdaptor {

	private static Logger logger = LoggerFactory.getLogger(FileListerAdapter.class);
	private static SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
	/**
	 * 1、根据要解压的文件获取目标路径
	 * 2、根据目标路径获取交易时间，作为目标目录下子目录
	 * 3、将扫描到的文件拷备到目录路径下
	 * 4、根据文件名称获取解压密码
	 * 5、根据是否有解压密码及压缩文件格式进行不同方式解压（支持rar及zip格式解压）
	 */
	@Override
	public void onFileCreate(File file) {
		logger.debug("监听文件创建方法开始执行");
		if(file != null && StringUtils.isNotEmpty(file.getPath()) && file.getName().toLowerCase().indexOf(".rar.szt") == -1
				&& file.getName().toLowerCase().indexOf(".zip.szt") == -1){
			String filePath = file.getPath().substring(0,file.getPath().lastIndexOf("\\"));
			Map<String,List<String>> map = FileCopyStart.getTargetDir(filePath);
			List<String> fileSuffix = map.get("fileSuffix");
			//判断当前文件是否需要文件过滤，如果是，则在拷备时需要对文件逐个拷备，不能进行文件夹拷备操作
			boolean filterFileFlag = false;
			if(fileSuffix != null && fileSuffix.size()>0){
				filterFileFlag = true;
			}
			List<String> targetDirs = map.get("targetDirs");
			List<String> sourcePath = map.get("sourcePath");
			if(targetDirs != null && targetDirs.size()>0){
				for(int i=0;i<targetDirs.size();i++){
					String targetDir = targetDirs.get(i);
					String sourceDir = sourcePath.get(i);
					if(StringUtils.isNotEmpty(targetDir)){
						try {
							String targetFilePath = targetDir+File.separator+file.getName().replace(" ","");
							//获取当前文件是否在文件过滤的范围内
							boolean flagUnpack = false;
							if(filterFileFlag){
								logger.debug("执行文件过滤拷备开始");
								if(file.isFile()){
									boolean flag = FileCopyStart.getfilterFile(file,fileSuffix);
									logger.debug("当前文件拷备是否过滤，false：否,true：是,获取到的值为："+flag);
									if(flag){
										break;
									}else{
										logger.debug("开始拷贝文件，文件源目录为："+file.getPath()+",文件目标目录为："+targetFilePath);
										FileUtils.copyFile(file, new File(targetFilePath));
										flagUnpack = true;
									}
								}else{
									cycleCopyFile(file,targetFilePath,"1",fileSuffix);									
								}
								logger.debug("执行文件过滤拷备结束");
							}else{
								logger.debug("onFileCreate开始拷贝文件，文件源目录为："+file.getPath()+",文件目标目录为："+targetFilePath);
								FileUtils.copyFile(file, new File(targetFilePath));	
								flagUnpack = true;
							}
							if(flagUnpack){
								if(file.getName().toLowerCase().indexOf(".rar")>-1 || file.getName().toLowerCase().indexOf(".zip")>-1){
									logger.debug("onFileCreate文件拷贝完成，开始进入文件解压缩方法");
									try {
										unpackFileCommons(new File(targetFilePath),targetFilePath,sourceDir,"onFileCreate");
									} catch (RarException e) {
										logger.error("解压文件出错，错误信息为："+e.getMessage(),e);
									}
								}
							}
						} catch (IOException e) {
							logger.error("拷贝文件时出错，错误信息为："+e.getMessage(),e);
						}
					}
				}
			}
		}
	}
	
	/**
	 * 
	 * @Description: 解压文件开始
	 * @author songy
	 * @date 2019年7月11日 下午2:18:44
	 * @date 2019年9月20日 下午13:50:44
	 * 由于java的jar包解压大部分都失败，暂时将程序调整为主要使用winrar进行文件解压操作
	 * 提高拷备的效率，解压启动新的线程单独执行
	 */
	public static void unpackFileCommons(File file,String targetFilePath,String targetDirReal,String callFunc) throws de.innosystec.unrar.exception.RarException{
		String winrarDirReal = targetDirReal;
		targetDirReal=targetDirReal+File.separator+file.getName().substring(0,file.getName().lastIndexOf("."))+File.separator;
		String unPackPwd = FileCopyStart.getUnPackFilePwd(file.getName());
		logger.debug("调用方为："+callFunc+",已进入unpackFileCommons文件解压方法，当前解压的文件路径为："+targetFilePath+",解压后的路径为："+winrarDirReal+File.separator+file.getName()+",当前文件解压所需密码为："+unPackPwd);
		FileCopyStart.taskExecutor.execute(new WinrarUnpackFile(file,winrarDirReal,unPackPwd));
		logger.debug("方法unpackFileCommons解压完成");
	}
	/**
	 * 
	 * @Description: 解压文件后，将解压目录下多个层级的目录下所有文件统一拷备到以压缩文件名为目录的目标目录下
	 * @author songy
	 * @date 2019年7月18日 上午10:29:47
	 * @param type:0表示解压后的文件拷备，1表示监控文件需要过滤的拷备
	 */
	public static void cycleCopyFile(File file,String targetDirReal,String type,List<String> fileSuffixs){
		File[] files = file.listFiles();
		String copyFilePath = targetDirReal+File.separator+file.getName();
		if(files != null && files.length>0){
			logger.debug("将多层级目录下文件统一拷备到目标目录下，临时目录为："+file.getPath()+"，目标目录为："+targetDirReal);
			for(File fileTemp:files){
				if(fileTemp.isDirectory()){
					cycleCopyFile(fileTemp,targetDirReal,type,fileSuffixs);
				}else if(fileTemp.isFile()){
					try {
						if("1".equals(type)){
							boolean flag = FileCopyStart.getfilterFile(fileTemp,fileSuffixs);
							if(flag){
								break;
							}
						}
						String targetFilePath = copyFilePath+File.separator+fileTemp.getName().replace(" ","");
						FileUtils.copyFile(fileTemp, new File(targetFilePath));
						if(fileTemp.isFile() && StringUtils.isNotEmpty(fileTemp.getName()) && fileTemp.getName().toLowerCase().indexOf(".rar.szt") == -1
								&& fileTemp.getName().toLowerCase().indexOf(".zip.szt") == -1 && (fileTemp.getName().toLowerCase().indexOf(".rar")>-1 || fileTemp.getName().toLowerCase().indexOf(".zip")>-1)){
							unpackFileCommons(new File(targetFilePath),targetFilePath,targetDirReal,"cycleCopyFile");							
						}
					} catch (IOException e) {
						logger.error("解压文件后进行文件拷备出错，错误信息为："+e.getMessage(),e);
					} catch (RarException e) {
						logger.error("循环拷备文件，有压缩文件进行解压时出错，错误信息为："+e.getMessage(),e);
					}
				}
			}
		}
	}
}
