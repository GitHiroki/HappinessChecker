package com.fksm.springboot;



import java.awt.event.InputEvent;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.bind.annotation.RestController;

import com.fksm.beans.Properties;
import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.client.MessageContentResponse;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.event.Event;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.message.ImageMessageContent;
import com.linecorp.bot.model.event.message.StickerMessageContent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.message.ImageMessage;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.StickerMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.spring.boot.annotation.EventMapping;
import com.linecorp.bot.spring.boot.annotation.LineMessageHandler;

import com.google.cloud.vision.v1.AnnotateImageRequest;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.BatchAnnotateImagesRequest;
import com.google.cloud.vision.v1.BatchAnnotateImagesResponse;
import com.google.cloud.vision.v1.EntityAnnotation;
import com.google.cloud.vision.v1.FaceAnnotation;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.Feature.Type;
import com.google.cloud.vision.v1.Image;
import com.google.cloud.vision.v1.ImageAnnotationContext;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.protobuf.ByteString;

@SpringBootApplication
@LineMessageHandler
@RestController
@ComponentScan("com.fksm.beans")
public class LineBotDemoSpringBootAppApplication extends SpringBootServletInitializer{
	private final Logger log = LoggerFactory.getLogger(LineBotDemoSpringBootAppApplication.class);
	@Autowired
	private Properties props;
	
	public static void main(String[] args) {
		SpringApplication.run(LineBotDemoSpringBootAppApplication.class, args);
	}
	
	@EventMapping
    public Message handleTextMessageEvent(MessageEvent<TextMessageContent> event) {
        log.info("event: " + event);
        
        final String originalMessageText = event.getMessage().getText() + System.lineSeparator() +  "あけましておめでとうロボ！";
        
        return new TextMessage(originalMessageText);
    }
	
//	@EventMapping
//	public Message handleImageEvent(MessageEvent<ImageMessageContent> event) {
//		final String channelToken = props.getChannelToken();
//		final LineMessagingClient client = LineMessagingClient.builder(channelToken).build();
//		
//		final MessageContentResponse messageContentResponse;
//		try {
//		    messageContentResponse = client.getMessageContent(event.getMessage().getId()).get();
//
//		} catch (InterruptedException | ExecutionException e) {
//		    e.printStackTrace();
//		    return new TextMessage("例外発生ロボ！ ");
//		}
//		
//		return new TextMessage("画像ありがとうロボ！");
//	}
	
	@EventMapping
	public void handleImageEvent(MessageEvent<ImageMessageContent> event) {
		
		final String channelToken = props.getChannelToken();
		final LineMessagingClient client = LineMessagingClient.builder(channelToken).build();
		
		final MessageContentResponse messageContentResponse;
		String tempImageFilePath = "";
		String detectResult = "";
		try {
		    messageContentResponse = client.getMessageContent(event.getMessage().getId()).get();
		    // 保存先のパス
		    tempImageFilePath = System.getProperty("user.dir") + "/" + "line_img_" + System.currentTimeMillis() + ".jpg";
		    
		    // ファイルの保存
		    Files.copy(messageContentResponse.getStream(), Paths.get(tempImageFilePath));
		    
		    detectResult = ImgDetect(tempImageFilePath);
		    
		} catch (InterruptedException | ExecutionException | IOException e) {
		    e.printStackTrace();
		}

		TextMessage tmsg = new TextMessage("画像ありがとう！");
		TextMessage detectMsg = new TextMessage(detectResult);
		// 下画像は表示されないけど返信はできた。(真っ黒な画像が送信された。)
		String imageUrl = "https://drive.google.com/file/d/1UjMPLbspAxliw90Kt78-Iu_-_j4IwCCi/view?usp=sharing";
		ImageMessage imsg = new ImageMessage(imageUrl, imageUrl);// URLはhttp始まりでないとダメらしい。
		ReplyMessage rm = new ReplyMessage(event.getReplyToken(), Arrays.asList(tmsg, imsg, detectMsg));
		
		client.replyMessage(rm);
	}
	
	private String ImgDetect(String filePath) {
		
		String detectResult = "";
		try {
			PrintStream outputResultPath = new PrintStream(new FileOutputStream(System.getProperty("user.dir") + "/" + "Result.txt"));
			
			 detectResult = detectText(filePath, outputResultPath);
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return detectResult;
	
	}

	private String detectText(String inputImgPath, PrintStream out) throws FileNotFoundException, IOException {
		List<AnnotateImageRequest> requests = new ArrayList<>();
		
		ByteString imgBytes;
		
		imgBytes = ByteString.readFrom(new FileInputStream(inputImgPath));
		Image img = Image.newBuilder().setContent(imgBytes).build();
//		Feature feat = Feature.newBuilder().setType(Type.TEXT_DETECTION).build();
		Feature feat = Feature.newBuilder().setType(Type.FACE_DETECTION).build();
		AnnotateImageRequest request = 
				AnnotateImageRequest.newBuilder().addFeatures(feat).setImage(img).build();
		requests.add(request);
		
		 
		
		try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
			BatchAnnotateImagesResponse response = client.batchAnnotateImages(requests);
			List<AnnotateImageResponse> responses = response.getResponsesList();
			StringBuilder sb = new StringBuilder();
			
			for (AnnotateImageResponse res : responses) {
				if (res.hasError()) {
					out.printf("Error:%s\n", res.getError().getMessage());
					return "";
				}
//				for (EntityAnnotation annotation : res.getTextAnnotationsList()) {
//					out.printf("Text: %s\n", annotation.getDescription());
//					out.printf("Position: %s\n", annotation.getBoundingPoly());
//					sb.append(String.format("%s\n", annotation.getDescription()));
//				}
				for (FaceAnnotation annotation : res.getFaceAnnotationsList()) {
					// 以下それぞれの文字列で点数(係数)を決める
					// 係数から数値をかけて点数を出すようにする
					// この際100点はなくてよい。80-95点の幅で調整しよう.
					out.printf("AngerLikelihood: %s\n", annotation.getAngerLikelihood());
					out.printf("JoyLikelihood: %s\n", annotation.getJoyLikelihood());
					out.printf("SurpriseLikelihood: %s\n", annotation.getSurpriseLikelihood());
					out.printf("BoundingPoly: %s\n", annotation.getBoundingPoly());
					
					sb.append(String.format("AngerLikelihood: %s\n", annotation.getAngerLikelihood()));
//					sb.append(String.format("AngerLikelihoodValue: %d\n", annotation.getAngerLikelihoodValue()));
					sb.append(String.format("JoyLikelihood: %s\n", annotation.getJoyLikelihood()));
//					sb.append(String.format("JoyLikelihoodValue(): %i\n", annotation.getJoyLikelihoodValue()));
					sb.append(String.format("SurpriseLikelihood(): %s\n", annotation.getSurpriseLikelihood()));
//					sb.append(String.format("SurpriseLikelihoodValue(): %i\n", annotation.getSurpriseLikelihoodValue()));
				}
			}
			return sb.toString();
		}
	}

	@EventMapping
	public Message handleStampEvent(MessageEvent<StickerMessageContent> event) {
		return new StickerMessage("1", "1");
	}
	
	@EventMapping
    public void handleDefaultMessageEvent(Event event) {
		System.out.println("event: " + event);
    }
}

