package com.fksm.beans;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * application.propertiesを取得するbeanクラス.
 * @author HIROKI
 *
 */
@Component
public class Properties {
	@Value("${line.bot.channelToken}")
	private String channelToken;

	public Properties() {
		//コンストラクタ
	}
	
	public String getChannelToken() {
		return channelToken;
	}

	public void setChannelToken(String channelToken) {
		this.channelToken = channelToken;
	}

}
