const ___startTime = Date.now();
const MAX_DETECTION_TIME = 30000; // 30秒最大检测时间
const DETECTION_INTERVAL = 200; // 200ms检测间隔
let videoDetectionAttempts = 0;
let maxDetectionAttempts = MAX_DETECTION_TIME / DETECTION_INTERVAL;
let enableVideoIntervalId = null; // 修复：用于清理enableVideo定时器
let foundVideo = null; // 优化：缓存找到的视频元素，避免重复处理

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
    // 优化：限制DOM查询数量，提升性能
    const potentialShadowHosts = [
        'video-player', 'xg-player', 'dplayer', 'prism-player',
        'custom-video', '.video-container', '[data-video]'
    ];
    
    // 先尝试常见的Shadow DOM宿主
    for (const selector of potentialShadowHosts) {
        const elements = document.querySelectorAll(selector);
        for (const element of elements) {
            if (element.shadowRoot) {
                const shadowVideo = element.shadowRoot.querySelector('video');
                if (shadowVideo) return shadowVideo;
            }
        }
    }
    
    // 回退方案：限制查询数量，避免性能问题
    const allElements = document.querySelectorAll('*');
    const maxElementsToCheck = Math.min(allElements.length, 100);
    for (let i = 0; i < maxElementsToCheck; i++) {
        const element = allElements[i];
        if (element.shadowRoot) {
            const shadowVideo = element.shadowRoot.querySelector('video');
            if (shadowVideo) return shadowVideo;
        }
    }
    return null;
}

// 增强的视频事件监听
function setupVideoEventListeners(video) {
    const events = ['loadstart', 'loadeddata', 'canplay', 'play', 'pause', 'error', 'ended'];
    
    // 保存事件处理器引用，便于后续清理
    if (!video._eventHandlers) {
        video._eventHandlers = {};
    }
    
    events.forEach(eventType => {
        const handler = function(e) {
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
        };
        
        video.addEventListener(eventType, handler);
        video._eventHandlers[eventType] = handler;
    });
}

// 新增：清理视频事件监听器
function cleanupVideoEventListeners(video) {
    if (video && video._eventHandlers) {
        Object.keys(video._eventHandlers).forEach(eventType => {
            const handler = video._eventHandlers[eventType];
            if (handler) {
                video.removeEventListener(eventType, handler);
            }
        });
        delete video._eventHandlers;
    }
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
    const elementsToHide = [];
    
    // 先收集需要隐藏的元素
    [...document.body.children].forEach((element) => {
        const tagName = element.tagName.toLowerCase();
        if (tagName != 'script' && tagName != 'video') {
            elementsToHide.push(element);
        }
    });
    
    // 隐藏元素并清理事件监听器
    elementsToHide.forEach(element => {
        element.style.display = 'none';
        
        // 清理元素的事件监听器，防止内存泄漏
        if (element.cloneNode) {
            const clone = element.cloneNode(false);
            element.parentNode.replaceChild(clone, element);
        }
    });
}

function addVideoPlayerMask(video) {
    try {
        clearInterval(my_pollingIntervalId);
        
        // 修复：清理enableVideo定时器
        if (enableVideoIntervalId) {
            clearInterval(enableVideoIntervalId);
            enableVideoIntervalId = null;
        }
        
        // 优化：缓存找到的视频
        foundVideo = video;
        
        document.body.appendChild(video);
        removeAllDivElements();
        video.style = 'width: 100%; height: 100%;object-fit: contain;';
        video.autoplay = true;
        document.body.style = 'width: 100vw; height: 100vh; margin: 0; min-width: 0; background: #000; padding: 0;';
        
        // 修复：检查Android接口存在性
        if (typeof Android !== 'undefined' && Android.changeVideoResolution) {
            Android.changeVideoResolution(1920, 1080);
        }
    } catch (error) {
        console.error('添加视频播放器遮罩时发生错误:', error);
        if (typeof Android !== 'undefined' && Android.onVideoError) {
            Android.onVideoError('视频播放器初始化失败: ' + error.message);
        }
    }
}

function enableVideo(video) {
    if (video.muted || video.volume != 1 || video.autoplay === false) {
        video.muted = false;
        video.autoplay = true;
        video.volume = 1;
    }else{
        // 修复：清理定时器ID，不是函数名
        if (enableVideoIntervalId) {
            clearInterval(enableVideoIntervalId);
            enableVideoIntervalId = null;
        }
    }
}

// 移除未使用的cleanAllStyle函数，保持代码简洁

// 添加清理函数，用于脚本卸载时调用
function cleanup() {
    if (my_pollingIntervalId) {
        clearInterval(my_pollingIntervalId);
    }
    if (enableVideoIntervalId) {
        clearInterval(enableVideoIntervalId);
        enableVideoIntervalId = null;
    }
    
    // 清理视频事件监听器
    if (foundVideo) {
        cleanupVideoEventListeners(foundVideo);
        foundVideo = null;
    }
}

function __initializetMain() {
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
        // 优化：如果已经找到视频，避免重复处理
        if (foundVideo) {
            return;
        }
        
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
                    
                    // 等待视频加载完成，避免重复添加监听器
                    const onLoadedMetadata = function() {
                        if (video.videoWidth > 0 && video.videoHeight > 0) {
                            addVideoPlayerMask(video);
                            console.log('视频元数据加载完成，尺寸:', video.videoWidth + 'x' + video.videoHeight);
                        }
                    };
                    
                    if (video.readyState >= 1) {
                        onLoadedMetadata();
                    } else {
                        video.addEventListener('loadedmetadata', onLoadedMetadata, { once: true });
                    }
                    
                    // 修复：保存定时器ID并确保只创建一个
                    if (!enableVideoIntervalId) {
                        enableVideoIntervalId = setInterval(enableVideo, 100, video);
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
const my_pollingIntervalId = setInterval(__initializetMain, DETECTION_INTERVAL);

// 页面卸载时清理资源，防止内存泄漏
window.addEventListener('beforeunload', cleanup);
window.addEventListener('unload', cleanup);
window.addEventListener('pagehide', cleanup);

// Android WebView特定：监听页面可见性变化
if (typeof document !== 'undefined' && document.addEventListener) {
    document.addEventListener('webkitvisibilitychange', function() {
        if (document.webkitVisibilityState === 'hidden') {
            cleanup();
        }
    });
}

// 处理WebView的销毁事件
if (typeof Android !== 'undefined' && Android.onPageUnload) {
    Android.onPageUnload = cleanup;
}