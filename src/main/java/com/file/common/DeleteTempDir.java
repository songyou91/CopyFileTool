package com.file.common;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.file.common.excel.ExcelDataObject;

public class DeleteTempDir {

	private static Logger logger = LoggerFactory.getLogger(DeleteTempDir.class);
	
	public void deleteTempDir(){
		List<ExcelDataObject> list = FileCopyStart.monitorDirList;
		if(list != null && list.size()>0){
			for(ExcelDataObject obj:list){
				String dateDir = FileCopyStart.getTradeDate(obj.getFieldsThree());
				String targetTempDir = obj.getFieldsThree()+File.separator+dateDir+File.separator+"tempUnpackDir";
				File file = new File(targetTempDir);
				if(file.exists()){
					try {
						logger.debug("定时删除目标目录下的临时解压目录，当前要删除的目录为："+targetTempDir);
						FileUtils.cleanDirectory(file);
						FileUtils.deleteDirectory(file);
					} catch (IOException e) {
						logger.error("定时删除目标临时目录出错，错误信息为："+e.getMessage(),e);
					}
				}
			}
		}
	}
}
