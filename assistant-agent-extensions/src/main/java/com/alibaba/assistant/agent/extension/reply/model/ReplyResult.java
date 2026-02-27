package com.alibaba.assistant.agent.extension.reply.model;

import java.util.HashMap;
import java.util.Map;

/**
 * 回复执行结果
 *
 * @author Assistant Agent Team
 */
public class ReplyResult {

	private boolean success;

	private String message;

	private Map<String, Object> metadata;

	private Object data;

	/**
	 * 是否已回复用户（即消息已发送到用户端）
	 * 用于标识此工具调用已经直接回复了用户，后续处理时可以跳过重复回复
	 */
	private boolean repliedToUser;

	public ReplyResult() {
		this.metadata = new HashMap<>();
		this.repliedToUser = false;
	}

	public ReplyResult(boolean success, String message, Map<String, Object> metadata, Object data) {
		this.success = success;
		this.message = message;
		this.metadata = metadata != null ? metadata : new HashMap<>();
		this.data = data;
		this.repliedToUser = false;
	}

	public ReplyResult(boolean success, String message, Map<String, Object> metadata, Object data, boolean repliedToUser) {
		this.success = success;
		this.message = message;
		this.metadata = metadata != null ? metadata : new HashMap<>();
		this.data = data;
		this.repliedToUser = repliedToUser;
	}

	public static ReplyResult success(String message) {
		return new ReplyResult(true, message, null, null);
	}

	public static ReplyResult success(String message, Object data) {
		return new ReplyResult(true, message, null, data);
	}

	/**
	 * 创建成功结果，并标记为已回复用户
	 * @param message 消息
	 * @param repliedToUser 是否已回复用户
	 * @return ReplyResult
	 */
	public static ReplyResult successWithReply(String message, boolean repliedToUser) {
		return new ReplyResult(true, message, null, null, repliedToUser);
	}

	public static ReplyResult failure(String message) {
		return new ReplyResult(false, message, null, null);
	}

	public static ReplyResult failure(String message, Map<String, Object> metadata) {
		return new ReplyResult(false, message, metadata, null);
	}

	// Getters and Setters
	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public Map<String, Object> getMetadata() {
		return metadata;
	}

	public void setMetadata(Map<String, Object> metadata) {
		this.metadata = metadata;
	}

	public Object getData() {
		return data;
	}

	public void setData(Object data) {
		this.data = data;
	}

	public void putMetadata(String key, Object value) {
		this.metadata.put(key, value);
	}

	public boolean isRepliedToUser() {
		return repliedToUser;
	}

	public void setRepliedToUser(boolean repliedToUser) {
		this.repliedToUser = repliedToUser;
	}

}

