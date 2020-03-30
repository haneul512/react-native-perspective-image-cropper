#import "CustomCropManager.h"
#import <React/RCTLog.h>
#import <Photos/Photos.h>
@implementation CustomCropManager

RCT_EXPORT_MODULE();

RCT_EXPORT_METHOD(crop:(NSDictionary *)points imageUri:(NSString *)imageUri callback:(RCTResponseSenderBlock)callback)
{
    if([imageUri hasPrefix:@"ph://"]) {
        //NSData *imageData = [NSData dataWithContentsOfURL:imageUri];
        PHFetchResult* fetchResult = [PHAsset fetchAssetsWithLocalIdentifiers:@[
         [imageUri stringByReplacingOccurrencesOfString:@"ph://" withString:@""]]
         options:nil];
        PHAsset *resultAsset = [fetchResult firstObject];
        //[resultAsset data]
        PHImageManager *manager = [PHImageManager defaultManager];
        PHImageRequestOptions *option = [PHImageRequestOptions new];
        option.synchronous = YES;
        [manager requestImageForAsset:resultAsset
                           targetSize:PHImageManagerMaximumSize
                          contentMode:PHImageContentModeDefault
                              options:option
                        resultHandler:^(UIImage * _Nullable image, NSDictionary * _Nullable info) {
            
            CIImage *ciImage = [CIImage imageWithCGImage:image.CGImage];
            ciImage = [ciImage imageByApplyingOrientation:image.imageOrientation];
            [self crop:points image:ciImage callback:callback];
            
           
        }];
         
    }
    else {
        NSString *parsedImageUri = [imageUri stringByReplacingOccurrencesOfString:@"file://" withString:@""];
        NSURL *fileURL = [NSURL fileURLWithPath:parsedImageUri];
        CIImage *ciImage = [CIImage imageWithContentsOfURL:fileURL];
        [self crop:points image:ciImage callback:callback];
    }
    
    
}

- (void)crop:(NSDictionary *)points image:(CIImage *)ciImage callback:(RCTResponseSenderBlock)callback {
    CGPoint newLeft = CGPointMake([points[@"topLeft"][@"x"] floatValue], [points[@"topLeft"][@"y"] floatValue]);
    CGPoint newRight = CGPointMake([points[@"topRight"][@"x"] floatValue], [points[@"topRight"][@"y"] floatValue]);
    CGPoint newBottomLeft = CGPointMake([points[@"bottomLeft"][@"x"] floatValue], [points[@"bottomLeft"][@"y"] floatValue]);
    CGPoint newBottomRight = CGPointMake([points[@"bottomRight"][@"x"] floatValue], [points[@"bottomRight"][@"y"] floatValue]);
    
    newLeft = [self cartesianForPoint:newLeft width:[points[@"width"] floatValue] height:[points[@"height"] floatValue] image:ciImage];
    newRight = [self cartesianForPoint:newRight width:[points[@"width"] floatValue]  height:[points[@"height"] floatValue] image:ciImage];
    newBottomLeft = [self cartesianForPoint:newBottomLeft width:[points[@"width"] floatValue]  height:[points[@"height"] floatValue] image:ciImage];
    newBottomRight = [self cartesianForPoint:newBottomRight width:[points[@"width"] floatValue]  height:[points[@"height"] floatValue] image:ciImage];
    
    
    
    NSMutableDictionary *rectangleCoordinates = [[NSMutableDictionary alloc] init];
    
    rectangleCoordinates[@"inputTopLeft"] = [CIVector vectorWithCGPoint:newLeft];
    rectangleCoordinates[@"inputTopRight"] = [CIVector vectorWithCGPoint:newRight];
    rectangleCoordinates[@"inputBottomLeft"] = [CIVector vectorWithCGPoint:newBottomLeft];
    rectangleCoordinates[@"inputBottomRight"] = [CIVector vectorWithCGPoint:newBottomRight];
    
    ciImage = [ciImage imageByApplyingFilter:@"CIPerspectiveCorrection" withInputParameters:rectangleCoordinates];
    
    CIContext *context = [CIContext contextWithOptions:nil];
    CGImageRef cgimage = [context createCGImage:ciImage fromRect:[ciImage extent]];
    UIImage *image = [UIImage imageWithCGImage:cgimage];
    if(image) {
       __block NSString* localId;
       [[PHPhotoLibrary sharedPhotoLibrary] performChanges:^{
           PHAssetChangeRequest *changeRequest = [PHAssetChangeRequest creationRequestForAssetFromImage:image];
           localId = [[changeRequest placeholderForCreatedAsset] localIdentifier];
           changeRequest.creationDate          = [NSDate date];
       } completionHandler:^(BOOL success, NSError *error) {
           if (success) {
               callback(@[[NSNull null], @{@"image": [NSString stringWithFormat:@"ph://%@",localId]}]);
               // NSData *imageToEncode = UIImageJPEGRepresentation(image, 0.8);
               //        callback(@[[NSNull null], @{@"image": [imageToEncode base64EncodedStringWithOptions:NSDataBase64Encoding64CharacterLineLength]}]);

           }
           else {
               NSLog(@"error saving to photos: %@", error);
           }
       }];
        //NSData *imageToEncode = UIImageJPEGRepresentation(image, 0.8);
        //callback(@[[NSNull null], @{@"image": [imageToEncode base64EncodedStringWithOptions:NSDataBase64Encoding64CharacterLineLength]}]);
    }
}

- (CGPoint)cartesianForPoint:(CGPoint)point width:(float)width height:(float)height image:(CIImage *)image {
    CGSize size = image.extent.size;
    float x = size.width * (point.x / width);
    float y = size.height * (point.y / height);
    y = size.height - y;
    return CGPointMake(x, y);
}

@end
