package com.file.common.unpack;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.file.common.FileCopyStart;
import com.github.junrar.Archive;
import com.github.junrar.VolumeManager;
import com.github.junrar.rarfile.FileHeader;

import de.innosystec.unrar.Archives;
import de.innosystec.unrar.exception.RarException;
import de.innosystec.unrar.rarfile.FileHeaders;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;

public class UnpackFile {

	public static final String SEPARATOR = File.separator;
	private static Logger logger = LoggerFactory.getLogger(UnpackFile.class);
	public static String str = "20190715";
	/**
	 * 解压指定RAR文件到指定的路径
	 * 
	 * @param srcRarFile
	 *            需要解压RAR文件
	 * @param destPath
	 *            指定解压路径
	 * @param password
	 *            压缩文件时设定的密码
	 * @throws IOException
	 */
	public static boolean unrarByPwd(File srcRarFile, String destPath, String password) throws IOException {
		boolean flag = true;
		logger.debug("unrarByPwd方法执行");
		if (null == srcRarFile || !srcRarFile.exists()) {
			throw new IOException("指定压缩文件不存在.");
		}
		if (!destPath.endsWith(SEPARATOR)) {
			destPath += SEPARATOR;
		}
		Archives archive = null;
		OutputStream unOut = null;
		String destDirName = "";
		File dir = null;
		try {
			archive = new Archives(srcRarFile, password, false);
			FileHeaders fileHeader = archive.nextFileHeader();
			while (null != fileHeader) {
				if (!fileHeader.isDirectory()) {
					// 1 根据不同的操作系统拿到相应的 destDirName 和 destFileName
					String destFileName = "";
					if (SEPARATOR.equals("/")) { // 非windows系统
						destFileName = (destPath + fileHeader.getFileNameW()).replaceAll("\\\\", "/");
						destDirName = destFileName.substring(0, destFileName.lastIndexOf("/"));
					} else { // windows系统
						destFileName = (destPath + fileHeader.getFileNameW()).replaceAll("/", "\\\\");
						destDirName = destFileName.substring(0, destFileName.lastIndexOf("\\"));
					}
					// 2创建文件夹
					dir = new File(destDirName);
					if (!dir.exists() || !dir.isDirectory()) {
						dir.mkdirs();
					}
					// 抽取压缩文件
					unOut = new FileOutputStream(new File(destFileName));
					archive.extractFile(fileHeader, unOut);
					unOut.flush();
					unOut.close();
				}
				fileHeader = archive.nextFileHeader();
			}
			archive.close();
		} catch (RarException e) {
			flag = false; 
			unOut.flush();
			unOut.close();
			logger.error("解压rar文件出现异常，已删除解压后的目录，异常信息为："+e.getMessage());
			if(dir != null){
				FileUtils.deleteDirectory(dir);
			}
		}
		logger.debug("unrarByPwd方法执行完成");
		return flag;
	}
	/**
	 * 
	 * @Description: 解压rar格式不带密码的文件
	 * @author songy
	 * @date 2019年7月8日 下午3:17:54
	 */
	public static boolean unrar(File srcRarFile, String destPath){
		boolean result = true; 
		File outFileDir = new File(destPath);
        if (!outFileDir.exists()) {
        	outFileDir.mkdirs();
        }
		try {
			logger.debug("开始解压rar格式文件，当前文件路径为："+srcRarFile+"，解压后的路径为："+destPath);
			InputStream inputStream = new FileInputStream(srcRarFile);
			Archive archive = new Archive(inputStream);
			FileHeader fileHeader = archive.nextFileHeader();
			if(fileHeader == null){
				result = false;
				return result;
			}
	        while (fileHeader != null) {
	            if (fileHeader.isDirectory()) {
	                fileHeader = archive.nextFileHeader();
	                continue;
	            }
	            String fileName = fileHeader.getFileNameW().isEmpty() ? fileHeader
						.getFileNameString() : fileHeader.getFileNameW();
	            File out = new File(destPath + fileName);
	            if (!out.exists()) {
	                if (!out.getParentFile().exists()) {
	                    out.getParentFile().mkdirs();
	                }
	                out.createNewFile();
	            }
	            FileOutputStream os = new FileOutputStream(out);
	            archive.extractFile(fileHeader, os);
	            os.close();
	            fileHeader = archive.nextFileHeader();
	        }
	        archive.close();
		}catch (Exception e) {
			if("unsupportedRarArchive".equals(e.getMessage())){
				result = false;
			}
			logger.error("解压rar文件出错，错误信息为："+e.getMessage(),e);
		}
		return result;
	}
	
	/**
	 * 
	 * @Description: 加压zip格式压缩文件,该方法有无密码都可进行解压
	 * @author songy
	 * @date 2019年7月8日 下午2:55:54
	 */
	public static boolean unzip(String zipSrcFilePath,String zipDestFilePath,String pwd) {
        File file = new File(zipDestFilePath);
        boolean flag = true;
        if (file.isDirectory() && !file.exists())
        {
            file.mkdirs();
        }
        try {
        	ZipFile zipFile = new ZipFile(zipSrcFilePath);
        	zipFile.setFileNameCharset("GBK");
            if (!zipFile.isValidZipFile())
            {
            	flag = false;
                throw new ZipException("压缩文件不合法，可能已经损坏！");
            }
            // 如果解压需要密码
            logger.debug("开始解压zip格式文件，当前文件路径为："+zipSrcFilePath+"，解压后的文件路径为："+zipDestFilePath);
            if(zipFile.isEncrypted()) {
            	logger.debug("当前解压zip文件需要密码，获取到的密码为："+pwd+",如果获取到密码为空，则不进行解压");
            	if(StringUtils.isNotEmpty(pwd)){
            		zipFile.setPassword(pwd);
            		zipFile.extractAll(zipDestFilePath);
            	}
            }else{
            	zipFile.extractAll(zipDestFilePath);
            }
        } catch (ZipException e) {
        	flag = false;
        	try {
				FileUtils.deleteDirectory(file);
			} catch (IOException e1) {
				logger.error("解压zip文件出错后删除目标路径同时出错，错误信息为："+e1.getMessage(),e1);
			}
            logger.error("解压zip格式文件出错，错误信息为："+e.getMessage(),e);
        }
        return flag;
    }
	
	/**
	 * 
	 * @Description: TODO(用一句话描述该文件做什么)
	 * @author songy
	 * @date 2019年7月10日 下午5:28:04
	 * 1、解压rar文件，如有exe文件，解压成功，但后台会报异常，
	 * 没有exe文件时，解压正常，解压后的中文文件名正常
	 * 2、解压zip压缩文件,带有中文的文件名正常，
	 * 3、解压zip文件密码错误，正确
	 * 4、解压zip文件密码正确，正确
	 * 5、带有密码的rar文件解压，密码错误,正确
	 * 5、带有密码的rar文件解压，密码正确，正确
	 */
	public static void main(String[] args) {
		File file = new File("F:\\ceshi\\20191104\\85010387 20191031.zip");
		System.out.println(file.getPath());
		System.out.println(file.getPath().replace(" ",""));
	}
}
