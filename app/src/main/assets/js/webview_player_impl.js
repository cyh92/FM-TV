const ___startTime = Date.now();
const MAX_DETECTION_TIME = 30000; // 30秒最大检测时间
const DETECTION_INTERVAL = 200; // 200ms检测间隔
let videoDetectionAttempts = 0;
const maxDetectionAttempts = MAX_DETECTION_TIME / DETECTION_INTERVAL;
let enableVideoTimer = null;

// 增强的视频检测函数
function findAllVideos() {
    const videos = [];

    // 检测常规 video 元素
    document.querySelectorAll('video').forEach(video => {
        if (video.src || video.currentSrc) {
            videos.push(video);
        }
    });

    // 检测 Shadow DOM 中的 video 元素
    const shadowVideo = getVideoParentShadowRoots();
    if (shadowVideo && !videos.includes(shadowVideo)) {
        videos.push(shadowVideo);
    }

    // 检测 iframe 中的视频（如果同源）
    try {
        document.querySelectorAll('iframe').forEach(iframe => {
            try {
                const iframeDoc = iframe.contentDocument || iframe.contentWindow.document;
                iframeDoc.querySelectorAll('video').forEach(video => {
                    if (video.src || video.currentSrc) {
                        videos.push(video);
                    }
                });
            } catch (e) {
                // 跨域 iframe，忽略
            }
        });
    } catch (e) {
        console.warn('无法检测iframe中的视频:', e.message);
    }

    return videos;
}

function getVideoParentShadowRoots() {
    const allElements = document.querySelectorAll('*');
    for (const element of allElements) {
        const shadowRoot = element.shadowRoot;
        if (shadowRoot) return shadowRoot.querySelector('video');
    }
    return null;
}

// 增强的视频事件监听
function setupVideoEventListeners(video) {
    const events = ['loadstart', 'loadeddata', 'canplay', 'play', 'pause', 'error', 'ended'];

    events.forEach(eventType => {
        video.addEventListener(eventType, function(e) {
            console.log(`视频事件: ${eventType}`, {
                src: video.src || video.currentSrc,
                readyState: video.readyState,
                paused: video.paused,
                duration: video.duration
            });

            if (eventType === 'error') {
                console.error('视频播放错误:', video.error);
                if (typeof Android !== 'undefined' && Android.onVideoError) {
                    Android.onVideoError('视频加载失败: ' + (video.error ? video.error.message : '未知错误'));
                }
            }

            if (eventType === 'canplay' && typeof Android !== 'undefined' && Android.onVideoFound) {
                Android.onVideoFound(1);
            }
        });
    });
}

function removeVideoPlayerControl() {
    const selectors = [
        '#control_bar_player',
        '#pic_in_pic_player',
        '.con.poster',
        'xg-controls',
        '.xgplayer-controls',
        '[data-kp-role=bottom-controls]',
        '.prism-controlbar',
        '.vjs-control-bar',
        '.playback-layer',
        '.control-bar',
        '.bitrate-layer',
        '.volume-layer',
        '.dplayer-controller',
        '._tdp_contrl'
    ];
    selectors.forEach(selector => {
        document.querySelectorAll(selector).forEach(element => {
            element.remove();
        });
    });
}

function removeAllDivElements() {
    [...document.body.children].forEach((element) => {
        const tagName = element.tagName.toLowerCase();
        if (tagName != 'script' && tagName != 'video' && tagName != 'head'){
            element.style.display = 'none';
        }
    });
}

function addVideoPlayerMask(video) {
    clearInterval(my_pollingIntervalId);
    if (enableVideoTimer) {
        clearInterval(enableVideoTimer);
        enableVideoTimer = null;
    }

    // 将video移到body下
    document.body.appendChild(video);

    // 隐藏其他元素
    removeAllDivElements();

    // 设置video样式
    video.style.width = '100%';
    video.style.height = '100%';
    video.style.objectFit = 'contain';
    video.style.position = 'absolute';
    video.style.top = '0';
    video.style.left = '0';
    video.style.zIndex = '9999';
    video.autoplay = true;

    // 设置body样式
    document.body.style.width = '100vw';
    document.body.style.height = '100vh';
    document.body.style.margin = '0';
    document.body.style.minWidth = '0';
    document.body.style.background = '#000';
    document.body.style.padding = '0';
    document.body.style.overflow = 'hidden';

    // 设置html样式
    document.documentElement.style.width = '100vw';
    document.documentElement.style.height = '100vh';
    document.documentElement.style.overflow = 'hidden';

    if (typeof Android !== 'undefined' && Android.changeVideoResolution) {
        Android.changeVideoResolution(1920, 1080);
    }
}

function enableVideo(video) {
    if (video.muted || video.volume != 1 || video.autoplay === false) {
        video.muted = false;
        video.autoplay = true;
        video.volume = 1;
        return true;
    }
    return false;
}

function cleanAllStyle() {
    // 移除所有样式表和style标签
    const styles = document.querySelectorAll('style,link[rel="stylesheet"]');
    styles.forEach(style => {
        style.remove();
    });

    // 清理除video和script外的所有元素的内联样式
    const allElements = document.querySelectorAll('*');
    allElements.forEach(element => {
        const tagName = element.tagName.toLowerCase();
        if (tagName !== 'video' && tagName !== 'script') {
            element.removeAttribute('style');
        }
    });
}

function __initializeMain() {
    videoDetectionAttempts++;

    if (videoDetectionAttempts > maxDetectionAttempts) {
        clearInterval(my_pollingIntervalId);
        console.error('视频检测超时，停止检测');
        if (typeof Android !== 'undefined' && Android.onVideoError) {
            Android.onVideoError('视频检测超时，未找到可播放的视频');
        }
        return;
    }

    try {
        const videos = findAllVideos();

        if (videos.length > 0) {
            console.log(`发现 ${videos.length} 个视频元素`);

            for (let video of videos) {
                if (video.src || video.currentSrc) {
                    setupVideoEventListeners(video);

                    console.info('视频源:', video.src || video.currentSrc);

                    // 尝试播放视频
                    if (video.paused) {
                        video.play().catch(e => {
                            console.warn('自动播放失败:', e.message);
                        });
                    }

                    video.volume = 1;
                    video.muted = false;

                    // 检查视频是否有尺寸
                    if (video.videoWidth > 0 && video.videoHeight > 0) {
                        addVideoPlayerMask(video);
                        console.log('视频初始化成功，尺寸:', video.videoWidth + 'x' + video.videoHeight);
                        return;
                    }

                    // 等待视频加载完成
                    video.addEventListener('loadedmetadata', function() {
                        if (video.videoWidth > 0 && video.videoHeight > 0) {
                            addVideoPlayerMask(video);
                            console.log('视频元数据加载完成，尺寸:', video.videoWidth + 'x' + video.videoHeight);
                        }
                    }, { once: true });

                    if (!enableVideoTimer) {
                        enableVideoTimer = setInterval(function() {
                            if (!enableVideo(video)) {
                                clearInterval(enableVideoTimer);
                                enableVideoTimer = null;
                            }
                        }, 100);
                    }
                    break; // 找到第一个可用视频就停止
                }
            }
        } else {
            // 每5秒报告一次检测状态
            if (videoDetectionAttempts % (5000 / DETECTION_INTERVAL) === 0) {
                console.log(`正在搜索视频元素... (第${videoDetectionAttempts}次尝试)`);
            }
        }
    } catch (error) {
        console.error('视频检测发生错误:', error);
        if (typeof Android !== 'undefined' && Android.onVideoError) {
            Android.onVideoError('视频检测发生错误: ' + error.message);
        }
    }
}
const my_pollingIntervalId = setInterval(__initializeMain, DETECTION_INTERVAL);