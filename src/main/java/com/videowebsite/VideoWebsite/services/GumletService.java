package com.videowebsite.VideoWebsite.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.swing.text.Document;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GumletService {

    @Value("${gumlet.collection.api.key}")
    private String apiKey;

    @Value("${gumlet.collection.value}")
    private String colKey;
    private static final String GUMLET_ASSET_URL = "https://api.gumlet.com/v1/video/assets/";
    private final Map<String, CachedSignedUrl> signedUrlCache = new ConcurrentHashMap<>();

    @Autowired
    private RestTemplate restTemplate;

    @Value("${gumlet.base.url}")
    private String BASE_URL;
    @Value("${gumlet.playlist.url}")
    private String PLAYLIST_URL;

    @Value("${gumlet.playlist.videos.url}")
    private String PLAYLIST_VIDEOS_URL;

    @Value("${gumlet.video.metadata.url}")
    private String VIDEO_METADATA_URL;

    public Object getListOfPlayListsByCollectionId() {
        return getFromGumlet(PLAYLIST_URL+colKey);
    }

    public Object getListOfVideosByPlaylistId(String playlistId) {
        String url = PLAYLIST_VIDEOS_URL.replace("playlist_id", playlistId);
        return getFromGumlet(url);
    }

    public Object getVideoMetaData(String videoId) {
        String url = VIDEO_METADATA_URL.replace("asset_id", videoId);
        return getFromGumlet(url);
    }

    private Object getFromGumlet(String url) {
        log.info("URL : ", url);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("Authorization", "Bearer " + apiKey);

        try {
            ResponseEntity<Object> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Object.class
            );
            return response.getBody();
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch data from Gumlet: " + url, e);
        }
    }

    public Object listAllVideos() {
        String url = BASE_URL + colKey;
        return getFromGumlet(url);
    }


    public ResponseEntity<byte[]> getVideoByDownloadUrl(String signedUrl, HttpEntity<String> entity){
        ResponseEntity<byte[]> gumletResponse = restTemplate.exchange(
                signedUrl,
                HttpMethod.GET,
                entity,
                byte[].class
        );
        return gumletResponse;
    }

    public Map<String, Object> getVideoById(String videoId) {
        String url = GUMLET_ASSET_URL + videoId;
        return (Map<String, Object>) getFromGumlet(url);
    }

    private record CachedSignedUrl(String url, Instant expiry) {}
}
