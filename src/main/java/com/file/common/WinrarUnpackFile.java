package com.file.common;

import java.io.File;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WinrarUnpackFile implements Runnable{

	private Logger logger = LoggerFactory.getLogger(WinrarUnpackFile.class);
	
	private File zipFile;
	private String destDir;
	private String password;
	
	public WinrarUnpackFile(File file,String desDirTemp,String pwd){
		this.zipFile =file;
		this.destDir = desDirTemp;
		this.password = pwd;
	}
	/**
     * 采用命令行方式解压文件
     * @param zipFile 压缩文件
     * @param destDir 解压结果路径
     * @return
     */
	@Override
	public void run() {
		// TODO Auto-generated method stub
		// 解决路径中存在/..格式的路径问题
        destDir = new File(destDir).getAbsoluteFile().getAbsolutePath();
        while(destDir.contains("..")) {
            String[] sepList = destDir.split("\\\\");
            destDir = "";
            for (int i = 0; i < sepList.length; i++) {
                if(!"..".equals(sepList[i]) && i < sepList.length -1 && "..".equals(sepList[i+1])) {
                    i++;
                } else {
                    destDir += sepList[i] + File.separator;
                }
            }
        }
        
        // 获取WinRAR.exe的路径
        String winrarPath = FileCopyStart.winrarPath;
        logger.debug("当前winrar执行文件所在路径为："+winrarPath+"当前文件解压所需密码为："+password);
        
        boolean bool = false;
        if (zipFile.exists()) {
        	// 开始调用命令行解压，参数-p表示解压密码，-ad表示解压到以压缩文件名称为目录的文件夹下
        	// -y表示解压时如有重复文件直接覆盖,-p123456 e -ad -y
        	//-p-忽略密码，如果文件有密码，则解压失败
        	String cmd = winrarPath + " e -p- -ad -y " + zipFile + " " + destDir;
        	if(StringUtils.isNotEmpty(password)){
        		cmd = winrarPath + " -p"+password+" e -ad -y " + zipFile + " " + destDir;
        	}
        	logger.debug("当前执行的解压命令为："+cmd);
        	try {
        		Process proc = Runtime.getRuntime().exec(cmd);
        		if (proc.waitFor() != 0) {
        			if (proc.exitValue() == 0) {
        				bool = false;
        			}
        		} else {
        			bool = true;
        		}
        	} catch (Exception e) {
        		logger.error("解压文件出错，错误信息为："+e.getMessage(),e);
        	}
        	logger.debug("目标路径为："+destDir+File.separator+zipFile.getName()+"文件解压结果为：" + (bool ? "成功" : "失败"));
        }
        
	}

}
