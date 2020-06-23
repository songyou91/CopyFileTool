package com.file.common;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.file.common.excel.ExcelDataObject;

public class ChangeMonitorDir {

	private static Logger logger = LoggerFactory.getLogger(ChangeMonitorDir.class);
	
	private void changeDir(){
		FileAlterationMonitor tempMonitor = FileCopyStart.monitor;
		Iterable<FileAlterationObserver> iters = tempMonitor.getObservers();
		logger.debug("定时任务启动，凌晨更新监控目录，先清除已经监控的路径，执行删除监控路径开始");
		for(Iterator iter = iters.iterator();iter.hasNext();){
			FileAlterationObserver observer = (FileAlterationObserver) iter.next();
			FileCopyStart.monitor.removeObserver(observer);
		}
		try {
			FileCopyStart.monitor.stop();
		} catch (Exception e) {
			logger.error("停止监听源目录文件变动情况，重新设置监控路径后在启动");
		}
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
		String currDate = sdf.format(new Date());
		FileCopyStart.currDate = currDate;
		logger.debug("更新当前日期时间，当前日期为："+FileCopyStart.currDate+",添加新的监控路径到监听器中");
		FileCopyStart.addMonitorDir("1");
	}
}
