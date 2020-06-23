package com.file.common.excel;

public class ExcelDataObject implements Comparable<ExcelDataObject>{

	private String fieldsFirst;//监控目录
	private String fieldsSecond;//上一个交易截止时间
	private String fieldsThree;//目标路径
	private String filedsFour;//过滤文件后缀名
	
	@ExcelField(title="fieldsFirst", type=2, sort=1)
	public String getFieldsFirst() {
		return fieldsFirst;
	}
	public void setFieldsFirst(String fieldsFirst) {
		this.fieldsFirst = fieldsFirst;
	}
	
	@ExcelField(title="fieldsSecond", type=2, sort=2)
	public String getFieldsSecond() {
		return fieldsSecond;
	}
	public void setFieldsSecond(String fieldsSecond) {
		this.fieldsSecond = fieldsSecond;
	}
	
	@ExcelField(title="fieldsThree", type=2, sort=3)
	public String getFieldsThree() {
		return fieldsThree;
	}
	public void setFieldsThree(String fieldsThree) {
		this.fieldsThree = fieldsThree;
	}
	@Override
	public int compareTo(ExcelDataObject o) {
		// TODO Auto-generated method stub
		return this.fieldsFirst.compareTo(o.fieldsFirst);
	}
	@ExcelField(title="filedsFour", type=2, sort=4)
	public String getFiledsFour() {
		return filedsFour;
	}
	public void setFiledsFour(String filedsFour) {
		this.filedsFour = filedsFour;
	}
	
}
